package com.example.demo.service;

import com.example.demo.dto.ContainerPlan;
import com.example.demo.dto.ComposePlan;
import com.example.demo.dto.StackAnalysis;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContainerizationService {

  private final GitHubService gitHub;
  public ContainerizationService(GitHubService gitHub) { this.gitHub = gitHub; }

  /** Calcule le plan Docker (Dockerfile existant ? à générer ? où builder ?) */
  public ContainerPlan plan(String repoUrl, String token, String repoFullName,
                            StackAnalysis analysis, String imageNameOverride, String registry) {
    ContainerPlan plan = new ContainerPlan();

    plan.setRegistry(isBlank(registry) ? "ghcr.io" : registry);
    plan.setImageName(isBlank(imageNameOverride) ? repoFullName : imageNameOverride);

    String wd = normalizeWd(analysis.getWorkingDirectory());
    plan.setWorkingDirectory(wd);
    plan.setDockerContext("."); // utilisé par docker/build-push-action (context)

    // 1) détecter Dockerfile/compose sous WD
    var files = gitHub.getRepositoryContents(repoUrl, token, ".".equals(wd) ? null : wd);
    if (files == null) files = List.of();

    boolean hasDockerfile = files.stream().anyMatch(f -> "Dockerfile".equals(String.valueOf(f.get("name"))));
    plan.setHasDockerfile(hasDockerfile);
    plan.setDockerfilePath(".".equals(wd) ? "Dockerfile" : wd + "/Dockerfile");

    boolean hasCompose = files.stream().anyMatch(f -> {
      var n = String.valueOf(f.get("name"));
      return n.equals("docker-compose.yml") || n.equals("docker-compose.yaml") || n.equals("compose.yaml");
    });
    plan.setHasCompose(hasCompose);

    var composeFiles = files.stream().map(f -> String.valueOf(f.get("name")))
        .filter(n -> n.equals("docker-compose.yml") || n.equals("docker-compose.yaml") || n.equals("compose.yaml"))
        .map(n -> ".".equals(wd) ? n : wd + "/" + n)
        .toList();
    plan.setComposeFiles(composeFiles);

    // 2) décider/générer Dockerfile si absent, ou si mauvais chemin (Maven vs Gradle)
    String buildTool = (analysis.getBuildTool()==null ? "" : analysis.getBuildTool().toLowerCase());
    if (!hasDockerfile) {
      plan.setShouldGenerateDockerfile(true);
      plan.setGeneratedDockerfileContent(generateDockerfile(analysis));
    } else {
      try {
        String existing = gitHub.getFileContent(repoUrl, token, plan.getDockerfilePath());
        boolean gradleButTarget   = "gradle".equals(buildTool) && existing.contains("/target/");
        boolean mavenButBuildLibs = "maven".equals(buildTool)  && existing.contains("/build/libs/");
        if (gradleButTarget || mavenButBuildLibs) {
          plan.setShouldGenerateDockerfile(true); // UPDATE_IF_EXISTS
          plan.setGeneratedDockerfileContent(generateDockerfile(analysis));
        }
      } catch (Exception ignore) { }
    }

    return plan;
  }

  /** Compose dev simple si Spring + DB */
  public ComposePlan planComposeForDev(StackAnalysis a) {
    ComposePlan cp = new ComposePlan();
    var isSpring = a.getStackType()!=null && a.getStackType().contains("SPRING_BOOT");
    var db = a.getDatabaseType();
    if (!isSpring || db==null || "NONE".equals(db)) { cp.shouldGenerateCompose = false; return cp; }

    String wd = normalizeWd(a.getWorkingDirectory());
    cp.shouldGenerateCompose = true;
    cp.composePath = "docker-compose.dev.yml";
    cp.content = composeDevYaml(wd, db, a.getDatabaseName());
    return cp;
  }

  // --- helpers ---

  private String generateDockerfile(StackAnalysis a) {
    String tool = a.getBuildTool()==null ? "" : a.getBuildTool().toLowerCase();

    if ("maven".equals(tool)) {
        String jv = safeJava(a.getJavaVersion());
        String wd  = normalizeWd(a.getWorkingDirectory()); // "." ou "backend"
      
        String wdCopy = ".".equals(wd) ? "." : wd; // pour les COPY
        String wdRun  = ".".equals(wd) ? "/src" : "/src/" + wd; // pour WORKDIR avant mvn
      
        return """
          # Multi-stage build for Spring Boot (Maven)
          FROM maven:3.9.6-eclipse-temurin-%1$s AS build
          WORKDIR /src
          COPY . .
          WORKDIR %2$s
          RUN mvn -B -DskipTests clean package
      
          FROM eclipse-temurin:%1$s-jre
          WORKDIR /app
          COPY --from=build %3$s/target/*.jar app.jar
          EXPOSE 8080
          ENTRYPOINT ["java","-jar","/app/app.jar"]
        """.formatted(jv, wdRun, ".".equals(wd) ? "/src" : "/src/" + wd);
      }
      

      if ("gradle".equals(tool)) {
        String jv = safeJava(a.getJavaVersion());
        String wd  = normalizeWd(a.getWorkingDirectory());
        String wdRun = ".".equals(wd) ? "/src" : "/src/" + wd;
      
        return """
          # Multi-stage build for Spring Boot (Gradle)
          FROM gradle:8.7.0-jdk%1$s AS build
          WORKDIR /src
          COPY . .
          WORKDIR %2$s
          RUN gradle build -x test --no-daemon
      
          FROM eclipse-temurin:%1$s-jre
          WORKDIR /app
          COPY --from=build %2$s/build/libs/*.jar app.jar
          EXPOSE 8080
          ENTRYPOINT ["java","-jar","/app/app.jar"]
        """.formatted(jv, wdRun);
      }
      

      if ("npm".equals(tool)) {
        String raw = null;
        if (a.getProjectDetails()!=null && a.getProjectDetails().get("nodeVersion")!=null) {
          raw = String.valueOf(a.getProjectDetails().get("nodeVersion"));
        }
        String baseTag = normalizeNodeBaseTagForDockerfile(raw); // ex: lts-alpine, 20-alpine
      
        return """
          FROM node:%s
          WORKDIR /app
          COPY package*.json ./
          RUN if [ -f package-lock.json ] || [ -f npm-shrinkwrap.json ]; then \
                npm ci --omit=dev; \
              else \
                npm install --omit=dev; \
              fi
          COPY . .
          EXPOSE 3000
          CMD ["npm","start"]
        """.formatted(baseTag);
      }
      
      
    return "FROM alpine:latest\nCMD [\"echo\",\"No known stack. Provide a Dockerfile.\"]\n";
  }

  // helper pour convertir "Latest", "lts/*", "20.x", "v20.10.1" -> tag Docker valide
private static String normalizeNodeBaseTagForDockerfile(String raw) {
  if (raw == null || raw.isBlank()) return "lts-alpine";
  String v = raw.trim().toLowerCase();

  // alias non numériques
  if (v.equals("latest") || v.equals("current") || v.equals("lts") || v.equals("lts/*")) {
    return "lts-alpine";
  }

  // 20 | 20.10 | 20.10.1
  if (v.matches("^v?\\d+(?:\\.\\d+){0,2}$")) {
    String major = v.replaceFirst("^v", "").split("\\.")[0];
    return major + "-alpine";
  }

  // 20.x
  java.util.regex.Matcher mx = java.util.regex.Pattern.compile("^(\\d+)\\.x$").matcher(v);
  if (mx.find()) return mx.group(1) + "-alpine";

  // fallback sûr
  return "lts-alpine";
}

  private String composeDevYaml(String wd, String dbType, String dbName) {
    boolean pg = "PostgreSQL".equals(dbType);
    String dbImage = pg ? "postgres:16" : "mysql:8";
    String port = pg ? "5432" : "3306";
    String dbEnv = pg
      ? "POSTGRES_DB="+(dbName==null?"my_database":dbName)+"\n      POSTGRES_USER=postgres\n      POSTGRES_PASSWORD=postgres"
      : "MYSQL_DATABASE="+(dbName==null?"my_database":dbName)+"\n      MYSQL_USER=mysql\n      MYSQL_PASSWORD=mysql\n      MYSQL_ROOT_PASSWORD=rootpassword";

    return """
      services:
        app:
          build:
            context: .
            dockerfile: %s/Dockerfile
          ports:
            - "8080:8080"
          environment:
            SPRING_PROFILES_ACTIVE: production
          depends_on: [database]
        database:
          image: %s
          environment:
            %s
          ports:
            - "%s:%s"
      """.formatted(".".equals(wd) ? "." : wd, dbImage, dbEnv, port, port);
  }

  private static String normalizeWd(String wd) {
    if (wd == null || wd.isBlank() || ".".equals(wd)) return ".";
    return wd.replaceFirst("^\\./","");
  }
  private static boolean isBlank(String s){ return s==null || s.isBlank(); }
  private static String safeJava(String v){ return isBlank(v) ? "17" : v; }
}
