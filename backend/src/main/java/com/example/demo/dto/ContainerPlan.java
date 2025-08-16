package com.example.demo.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ContainerPlan {
  // Image/registry
  private String registry = "ghcr.io";   // par défaut
  private String imageName;              // ex: org/repo ou override

  // Contexte du projet
  private String workingDirectory = "."; // ".", "backend", ...
  private String dockerContext = ".";    // utilisé par docker/build-push-action

  // Dockerfile
  private boolean hasDockerfile;
  private String dockerfilePath;         // "Dockerfile" ou "backend/Dockerfile"
  private boolean shouldGenerateDockerfile;
  private String generatedDockerfileContent;

  // Compose (optionnel)
  private boolean hasCompose;
  private List<String> composeFiles;

  // Pratique pour alimenter le template CI
  public Map<String, String> placeholders() {
    Map<String, String> m = new HashMap<>();
    m.put("registry", registry);
    m.put("imageName", imageName);
    m.put("dockerfilePath", dockerfilePath);
    m.put("dockerContext", dockerContext);
    return m;
  }
}
