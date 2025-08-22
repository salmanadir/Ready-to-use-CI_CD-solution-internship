package com.example.demo.service;

import com.example.demo.dto.ContainerPlan;
import com.example.demo.dto.StackAnalysis;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ContainerizationService {

  private final GitHubService gitHub;
  private final DockerfileGenerationService dockerfileGen;

  public ContainerizationService(GitHubService gitHub, DockerfileGenerationService dockerfileGen) {
    this.gitHub = gitHub;
    this.dockerfileGen = dockerfileGen;
  }

  /** Calcule le plan Docker (SINGLE) — Dockerfile existant ? à générer ? où builder ? */
  public ContainerPlan plan(String repoUrl, String token, String repoFullName,
                            StackAnalysis analysis, String imageNameOverride, String registry) {
    ContainerPlan plan = new ContainerPlan();

    plan.setRegistry(isBlank(registry) ? "ghcr.io" : registry);

    // GHCR préfère lowercase pour l'image
    String img = isBlank(imageNameOverride) ? repoFullName : imageNameOverride;
    plan.setImageName(img == null ? null : img.toLowerCase());

    String wd = normalizeWd(analysis.getWorkingDirectory());
    plan.setWorkingDirectory(wd);

    // Build context: Node → sous-dossier, Java → racine
    String tool = (analysis.getBuildTool() == null ? "" : analysis.getBuildTool().toLowerCase());
    String stack = analysis.getStackType() == null ? "" : analysis.getStackType().toLowerCase();
    boolean isNode = "npm".equals(tool) || stack.contains("node_js") || stack.contains("node");
    if (isNode) {
      plan.setDockerContext(".".equals(wd) ? "." : wd); // ex: "frontend"
    } else {
      plan.setDockerContext("."); // Java par défaut
    }

    // === 1) Détection Dockerfile dans le WD
    var files = gitHub.getRepositoryContents(repoUrl, token, ".".equals(wd) ? null : wd);
    if (files == null) files = List.of();

    boolean hasDockerfile = files.stream().anyMatch(f -> "Dockerfile".equals(String.valueOf(f.get("name"))));
    plan.setHasDockerfile(hasDockerfile);
    plan.setDockerfilePath(".".equals(wd) ? "Dockerfile" : wd + "/Dockerfile");

    // === 2) Générer si absent ou incohérent (Maven/Gradle croisés)
    if (hasDockerfile) {
      try {
        String existing = gitHub.getFileContent(repoUrl, token, plan.getDockerfilePath());
        plan.setExistingDockerfileContent(existing);

        // Preview = existant
        plan.setPreviewDockerfileContent(existing);
        plan.setPreviewSource("existing");

        boolean gradleButTarget   = "gradle".equals(tool) && existing.contains("/target/");
        boolean mavenButBuildLibs = "maven".equals(tool)  && existing.contains("/build/libs/");

        if (gradleButTarget || mavenButBuildLibs) {
          plan.setShouldGenerateDockerfile(true);
          String generated = dockerfileGen.generate(analysis);
          plan.setGeneratedDockerfileContent(generated);
          plan.setProposedDockerfileContent(generated);
        }
      } catch (Exception ignore) { /* non bloquant */ }
    } else {
      // pas de Dockerfile -> on génère & on prévisualise la proposition
      plan.setShouldGenerateDockerfile(true);
      String generated = dockerfileGen.generate(analysis);
      plan.setGeneratedDockerfileContent(generated);
      plan.setProposedDockerfileContent(generated);
      plan.setPreviewDockerfileContent(generated);
      plan.setPreviewSource("generated");
    }

    return plan;
  }

  /**
   * Calcule un plan Docker PAR SERVICE (MULTI).
   * `services` = liste issue de l'endpoint analyze (mode "multi") :
   *   { id, stackType, workingDirectory, buildTool, projectDetails, javaVersion, orchestrator, ... }
   */
  public List<ServiceContainerPlan> planMulti(String repoUrl,
                                              String token,
                                              String repoFullName,
                                              List<Map<String, Object>> services,
                                              String registry) {
    List<ServiceContainerPlan> out = new ArrayList<>();
    if (services == null) return out;

    String reg = isBlank(registry) ? "ghcr.io" : registry;
    String baseImg = (repoFullName == null ? "owner/repo" : repoFullName.toLowerCase());

    int idx = 0;
    for (Map<String, Object> s : services) {
      String id         = stringOrDefault(s.get("id"), "service-" + (idx++));
      String stackType  = stringOrNull(s.get("stackType"));
      String wd         = stringOrDefault(s.get("workingDirectory"), ".");
      String tool       = stringOrNull(s.get("buildTool"));
      String javaVer    = stringOrNull(s.get("javaVersion"));
      @SuppressWarnings("unchecked")
      Map<String, Object> details = (Map<String, Object>) s.get("projectDetails");

      // Construire un StackAnalysis minimal par service
      StackAnalysis a = new StackAnalysis(
              stackType,
              javaVer,
              "github-actions",
              wd,
              null
      );
      if (tool != null) a.setBuildTool(tool);
      a.setProjectDetails(details);

      // imageName par service : owner/repo-<id>
      String imageNameOverride = baseImg + "-" + id;

      // réutilise la logique single
      ContainerPlan base = plan(repoUrl, token, repoFullName, a, imageNameOverride, reg);

      // Wrap vers un plan “multi” simplifié pour le contrôleur
      ServiceContainerPlan scp = ServiceContainerPlan.from(id, base);
      out.add(scp);
    }

    return out;
  }

  // --- helpers ---
  private static String normalizeWd(String wd) {
    if (wd == null || wd.isBlank() || ".".equals(wd)) return ".";
    return wd.replaceFirst("^\\./","");
  }
  private static boolean isBlank(String s){ return s==null || s.isBlank(); }

  private static String stringOrNull(Object o){ return (o==null || "null".equals(String.valueOf(o))) ? null : String.valueOf(o); }
  private static String stringOrDefault(Object o, String def){ String v = stringOrNull(o); return v==null?def:v; }

  // ===============================
  // DTO interne pour le mode MULTI
  // ===============================
  public static class ServiceContainerPlan {
    private String serviceId;
    private String workingDirectory;

    private boolean hasDockerfile;
    private String dockerfilePath;

    private boolean shouldGenerateDockerfile;
    private String existingDockerfileContent;
    private String generatedDockerfileContent;
    private String previewDockerfileContent;
    private String previewSource;

    private String registry = "ghcr.io";
    private String imageName;

    public static ServiceContainerPlan from(String id, ContainerPlan base){
      ServiceContainerPlan p = new ServiceContainerPlan();
      p.serviceId = id;
      p.workingDirectory = base.getWorkingDirectory();
      p.hasDockerfile = base.isHasDockerfile();
      p.dockerfilePath = base.getDockerfilePath();
      p.shouldGenerateDockerfile = base.isShouldGenerateDockerfile();
      p.existingDockerfileContent = base.getExistingDockerfileContent();
      p.generatedDockerfileContent = base.getGeneratedDockerfileContent();
      p.previewDockerfileContent = base.getPreviewDockerfileContent();
      p.previewSource = base.getPreviewSource();
      p.registry = base.getRegistry();
      p.imageName = base.getImageName();
      return p;
    }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }

    public boolean isHasDockerfile() { return hasDockerfile; }
    public void setHasDockerfile(boolean hasDockerfile) { this.hasDockerfile = hasDockerfile; }

    public String getDockerfilePath() { return dockerfilePath; }
    public void setDockerfilePath(String dockerfilePath) { this.dockerfilePath = dockerfilePath; }

    public boolean isShouldGenerateDockerfile() { return shouldGenerateDockerfile; }
    public void setShouldGenerateDockerfile(boolean shouldGenerateDockerfile) { this.shouldGenerateDockerfile = shouldGenerateDockerfile; }

    public String getExistingDockerfileContent() { return existingDockerfileContent; }
    public void setExistingDockerfileContent(String existingDockerfileContent) { this.existingDockerfileContent = existingDockerfileContent; }

    public String getGeneratedDockerfileContent() { return generatedDockerfileContent; }
    public void setGeneratedDockerfileContent(String generatedDockerfileContent) { this.generatedDockerfileContent = generatedDockerfileContent; }

    public String getPreviewDockerfileContent() { return previewDockerfileContent; }
    public void setPreviewDockerfileContent(String previewDockerfileContent) { this.previewDockerfileContent = previewDockerfileContent; }

    public String getPreviewSource() { return previewSource; }
    public void setPreviewSource(String previewSource) { this.previewSource = previewSource; }

    public String getRegistry() { return registry; }
    public void setRegistry(String registry) { this.registry = registry; }

    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }
  }
}
