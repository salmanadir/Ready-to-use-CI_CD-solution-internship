package com.example.demo.dto;

import com.example.demo.service.GitHubService;

import lombok.Data;

public class WorkflowGenerationRequest {
    
    private Long repoId;
    private StackAnalysis techStackInfo;
    private FileHandlingStrategy fileHandlingStrategy; 
    private DockerOptions docker;
    
    // Enum pour définir la stratégie de gestion des fichiers
    public enum FileHandlingStrategy {
        UPDATE_IF_EXISTS,    // Mettre à jour si existe, créer sinon
        CREATE_NEW_ALWAYS,   // Toujours créer un nouveau fichier 
        FAIL_IF_EXISTS      // Ne rien faire si le fichier existe
    }
    
    public WorkflowGenerationRequest() {}
    
    public WorkflowGenerationRequest(Long repoId, StackAnalysis techStackInfo) {
        this.repoId = repoId;
        this.techStackInfo = techStackInfo;
        this.fileHandlingStrategy = FileHandlingStrategy.UPDATE_IF_EXISTS; // Défaut
    }
    
    public DockerOptions getDocker() {
        return docker;
    }
    
    public void setDocker(DockerOptions docker) {
        this.docker = docker;
    }
    
    public Long getRepoId() {
        return repoId;
    }
    
    public void setRepoId(Long repoId) {
        this.repoId = repoId;
    }
    
    public StackAnalysis getTechStackInfo() {
        return techStackInfo;
    }
    
    public void setTechStackInfo(StackAnalysis techStackInfo) {
        this.techStackInfo = techStackInfo;
    }
 
    
    public FileHandlingStrategy getFileHandlingStrategy() {
        return fileHandlingStrategy != null ? fileHandlingStrategy : FileHandlingStrategy.CREATE_NEW_ALWAYS;
    }
    
    public void setFileHandlingStrategy(FileHandlingStrategy fileHandlingStrategy) {
        this.fileHandlingStrategy = fileHandlingStrategy;
    }
@Data
    public static class DockerOptions {
    public boolean enable = true;                     // activer la partie docker dans la CI
    public String registry = "ghcr.io";               // registre (par défaut GHCR)
    public String imageNameOverride;                  // ex: org/custom-name (sinon ${{ github.repository }})
    public boolean generateCompose = false;           // créer docker-compose.dev.yml ?
    public GitHubService.FileHandlingStrategy dockerfileStrategy = GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS;
    public GitHubService.FileHandlingStrategy composeStrategy    = GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS;
  }
}