package com.example.demo.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ContainerPlan {
  // Image/registry
  private String registry = "ghcr.io";
  private String imageName;

  // Contexte du projet
  private String workingDirectory;
  private String dockerContext = ".";

  // Dockerfile
  private boolean hasDockerfile;
  private String dockerfilePath;               // "Dockerfile" ou "dir/Dockerfile"
  private boolean shouldGenerateDockerfile;

  // Contenus
  private String existingDockerfileContent;    // lu du repo (si présent)
  private String generatedDockerfileContent;   // contenu généré (si absent/incohérent)
  private String proposedDockerfileContent;    // alias du generated (pour clarté)
  private String previewDockerfileContent;     // ***contenu que le front DOIT afficher***
  private String previewSource;                // "existing" | "generated"

  // Compose (optionnel)
  private boolean hasCompose;
  private List<String> composeFiles;

  // Pour le template CI
  public Map<String, String> placeholders() {
    Map<String, String> m = new HashMap<>();
    m.put("registry", registry);
    m.put("imageName", imageName);
    m.put("dockerfilePath", dockerfilePath);
    m.put("dockerContext", dockerContext);
    return m;
  }
}
