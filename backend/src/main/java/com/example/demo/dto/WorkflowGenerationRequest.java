package com.example.demo.dto;

import com.example.demo.service.GitHubService;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkflowGenerationRequest {

    // --- champs principaux ---
    private Long repoId;

    // SINGLE mode: infos d’un seul service
    private StackAnalysis techStackInfo;

    // MULTI mode: tableau tel que renvoyé par /analyze (mode "multi")
    // ex: [{ id, stackType, workingDirectory, buildTool, projectDetails, javaVersion, orchestrator, ... }]
    private List<Map<String, Object>> services;

    // stratégie d’écriture du fichier workflow
    private FileHandlingStrategy fileHandlingStrategy = FileHandlingStrategy.UPDATE_IF_EXISTS;

    // options docker (registry, overrides, stratégies dockerfile/compose)
    private DockerOptions docker;

    // ----- enum -----
    public enum FileHandlingStrategy {
        UPDATE_IF_EXISTS,    // Mettre à jour si existe, créer sinon (défaut)
        CREATE_NEW_ALWAYS,   // Toujours créer un nouveau fichier
        FAIL_IF_EXISTS       // Échouer si le fichier existe
    }

    // ----- ctors -----
    public WorkflowGenerationRequest() {}

    public WorkflowGenerationRequest(Long repoId, StackAnalysis techStackInfo) {
        this.repoId = repoId;
        this.techStackInfo = techStackInfo;
        this.fileHandlingStrategy = FileHandlingStrategy.UPDATE_IF_EXISTS;
    }

    // ----- getters avec défaut cohérent -----
    public FileHandlingStrategy getFileHandlingStrategy() {
        return (fileHandlingStrategy != null) ? fileHandlingStrategy : FileHandlingStrategy.UPDATE_IF_EXISTS;
    }

    // ----- sous-objet options docker -----
    @Data
    public static class DockerOptions {
        public boolean enable = true;                     // activer la partie docker dans la CI
        public String registry = "ghcr.io";               // registre (par défaut GHCR)
        public String imageNameOverride;                  // ex: org/custom-name (sinon repo)
        public boolean generateCompose = false;           // créer docker-compose.dev.yml ?
        public GitHubService.FileHandlingStrategy dockerfileStrategy =
                GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS;
        public GitHubService.FileHandlingStrategy composeStrategy =
                GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS;

        public DockerOptions() {}
    }
}
