package com.example.demo.dto;

import com.example.demo.model.TechStackInfo;

public class WorkflowGenerationRequest {
    
    private Long repoId;
    private TechStackInfo techStackInfo;
    
    
    
    private FileHandlingStrategy fileHandlingStrategy; 
    
    // Enum pour définir la stratégie de gestion des fichiers
    public enum FileHandlingStrategy {
        UPDATE_IF_EXISTS,    // Mettre à jour si existe, créer sinon
        CREATE_NEW_ALWAYS,   // Toujours créer un nouveau fichier 
        FAIL_IF_EXISTS      // Ne rien faire si le fichier existe
    }
    
    public WorkflowGenerationRequest() {}
    
    public WorkflowGenerationRequest(Long repoId, TechStackInfo techStackInfo) {
        this.repoId = repoId;
        this.techStackInfo = techStackInfo;
        this.fileHandlingStrategy = FileHandlingStrategy.UPDATE_IF_EXISTS; // Défaut
    }
    
    
    public Long getRepoId() {
        return repoId;
    }
    
    public void setRepoId(Long repoId) {
        this.repoId = repoId;
    }
    
    public TechStackInfo getTechStackInfo() {
        return techStackInfo;
    }
    
    public void setTechStackInfo(TechStackInfo techStackInfo) {
        this.techStackInfo = techStackInfo;
    }
 
    
    public FileHandlingStrategy getFileHandlingStrategy() {
        return fileHandlingStrategy != null ? fileHandlingStrategy : FileHandlingStrategy.CREATE_NEW_ALWAYS;
    }
    
    public void setFileHandlingStrategy(FileHandlingStrategy fileHandlingStrategy) {
        this.fileHandlingStrategy = fileHandlingStrategy;
    }
}