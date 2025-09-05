package com.example.demo.service;

import com.example.demo.dto.StackAnalysis;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DockerfileGenerationService {

  /** Génère le contenu du Dockerfile en fonction de la stack détectée. */
  public String generate(StackAnalysis a) {
    String tool = a.getBuildTool()==null ? "" : a.getBuildTool().toLowerCase();
    String wd   = normalizeWd(a.getWorkingDirectory());

    if ("maven".equals(tool)) {
      String jv = safeJava(a.getJavaVersion());
      String wdRun = ".".equals(wd) ? "/src" : "/src/" + wd;
      String jarSrc = ".".equals(wd) ? "/src" : "/src/" + wd;
      return String.format("""
        # Spring Boot (Maven) - multi-stage
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
      """, jv, wdRun, jarSrc);
    }

    if ("gradle".equals(tool)) {
      String jv = safeJava(a.getJavaVersion());
      String wdRun = ".".equals(wd) ? "/src" : "/src/" + wd;
      return String.format("""
        # Spring Boot (Gradle) - multi-stage
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
      """, jv, wdRun);
    }

    if ("npm".equals(tool)) {
      String base = nodeBaseTag(a); // lts-alpine / 20-alpine…
      return String.format("""
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
      """, base);
    }

    return "FROM alpine:latest\nCMD [\"echo\",\"Provide a Dockerfile.\"]\n";
  }

  /**
   * Overload pratique pour le MODE MULTI:
   * construit un StackAnalysis minimal à partir des infos d’un service.
   */
  public String generate(String stackType,
                         String buildTool,
                         String javaVersion,
                         String workingDirectory,
                         Map<String, Object> projectDetails) {
    StackAnalysis a = new StackAnalysis(
        stackType,
        javaVersion,
        "github-actions",
        workingDirectory,
        null
    );
    a.setBuildTool(buildTool);
    a.setProjectDetails(projectDetails);
    return generate(a);
  }

  // ---------------------------------------------------

  private static String nodeBaseTag(StackAnalysis a) {
    String raw = a.getProjectDetails()!=null ? String.valueOf(a.getProjectDetails().get("nodeVersion")) : null;
    if (raw==null || raw.isBlank() || raw.equalsIgnoreCase("latest") || raw.equalsIgnoreCase("lts") || raw.equalsIgnoreCase("lts/*"))
      return "lts-alpine";
    String v = raw.toLowerCase().replaceFirst("^v","");
    if (v.matches("^\\d+(?:\\.\\d+){0,2}$")) return v.split("\\.")[0] + "-alpine"; // 20, 20.10 → 20-alpine
    if (v.matches("^(\\d+)\\.x$")) return v.replace(".x","") + "-alpine";
    return "lts-alpine";
  }

  private static String safeJava(String v){ return (v==null || v.isBlank()) ? "17" : v; }
  private static String normalizeWd(String wd){ return (wd==null || wd.isBlank() || ".".equals(wd)) ? "." : wd.replaceFirst("^\\./",""); }
}