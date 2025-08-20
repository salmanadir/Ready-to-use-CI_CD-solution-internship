package com.example.demo.service;

import com.example.demo.dto.ContainerPlan;
import com.example.demo.dto.ComposePlan;
import com.example.demo.dto.StackAnalysis;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContainerizationService {

  private final GitHubService gitHub;
  private final DockerfileGenerationService dockerfileGen;

  public ContainerizationService(GitHubService gitHub, DockerfileGenerationService dockerfileGen) {
    this.gitHub = gitHub;
    this.dockerfileGen = dockerfileGen;
  }

  /** Calcule le plan Docker (Dockerfile existant ? à générer ? où builder ?) */
  public ContainerPlan plan(String repoUrl, String token, String repoFullName,
                            StackAnalysis analysis, String imageNameOverride, String registry) {
    ContainerPlan plan = new ContainerPlan();

    plan.setRegistry(isBlank(registry) ? "ghcr.io" : registry);

    // GHCR préfère lowercase pour l'image
    String img = isBlank(imageNameOverride) ? repoFullName : imageNameOverride;
    plan.setImageName(img == null ? null : img.toLowerCase());

    String wd = normalizeWd(analysis.getWorkingDirectory());
    plan.setWorkingDirectory(wd);

    // important : le build context doit pointer sur le WD
    plan.setDockerContext(".");

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

    // 2) décider/générer Dockerfile si absent, ou incohérent (Maven/Gradle croisés)
    String tool = (analysis.getBuildTool() == null ? "" : analysis.getBuildTool().toLowerCase());

    if (hasDockerfile) {
      try {
        String existing = gitHub.getFileContent(repoUrl, token, plan.getDockerfilePath());
        plan.setExistingDockerfileContent(existing);

        // 👉 PREVIEW = EXISTANT (toujours si présent)
        plan.setPreviewDockerfileContent(existing);
        plan.setPreviewSource("existing");

        // Détecter incohérence (ex: projet Gradle mais COPY .../target/*.jar)
        boolean gradleButTarget   = "gradle".equals(tool) && existing.contains("/target/");
        boolean mavenButBuildLibs = "maven".equals(tool)  && existing.contains("/build/libs/");

        if (gradleButTarget || mavenButBuildLibs) {
          // On PROPOSE un Dockerfile corrigé, MAIS on NE CHANGE PAS la preview
          plan.setShouldGenerateDockerfile(true);
          String generated = dockerfileGen.generate(analysis);
          plan.setGeneratedDockerfileContent(generated);
          plan.setProposedDockerfileContent(generated);
          // NOTE: on NE touche PAS à previewDockerfileContent / previewSource
        }
      } catch (Exception ignore) { /* non bloquant */ }
    } else {
      // pas de Dockerfile -> on génère & on prévisualise la proposition
      plan.setShouldGenerateDockerfile(true);
      String generated = dockerfileGen.generate(analysis);
      plan.setGeneratedDockerfileContent(generated);
      plan.setProposedDockerfileContent(generated);

      // 👉 PREVIEW = GÉNÉRÉ (si absent)
      plan.setPreviewDockerfileContent(generated);
      plan.setPreviewSource("generated");
    }

    return plan;
  }

  /** Compose dev simple si Spring + DB (inchangé) */
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

  // --- helpers déjà existants (composeDevYaml / normalizeWd / isBlank) ---
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
}
