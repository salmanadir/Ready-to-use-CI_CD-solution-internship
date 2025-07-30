package com.example.demo.service;

import org.kohsuke.github.GHContentUpdateResponse;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class GitHubService {

    /**
     * Push un fichier workflow vers GitHub selon la stratégie choisie
     * @param token Token GitHub de l'utilisateur
     * @param repoFullName Nom complet du repository (owner/repo)
     * @param branch Branche cible
     * @param filePath Chemin du fichier dans le repository
     * @param content Contenu du fichier
     * @param strategy Stratégie de gestion des fichiers existants
     * @return Résultat du push avec informations sur l'action effectuée
     * @throws IOException En cas d'erreur de communication avec GitHub
     */
    public PushResult pushWorkflowToGitHub(String token, String repoFullName, String branch, String filePath, 
                                           String content, FileHandlingStrategy strategy) throws IOException {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("GitHub token cannot be null or empty");
        }
        if (repoFullName == null || repoFullName.isEmpty()) {
            throw new IllegalArgumentException("Repository full name cannot be null or empty");
        }
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }

        GitHub github = GitHub.connectUsingOAuth(token);
        GHRepository repository = github.getRepository(repoFullName);
        String targetBranch = branch != null ? branch : "main";
        
        boolean fileExists = fileExists(token, repoFullName, targetBranch, filePath);
        
        switch (strategy) {
            case UPDATE_IF_EXISTS:
                return updateOrCreate(repository, targetBranch, filePath, content, fileExists);
                
            case CREATE_NEW_ALWAYS:
                return createNewFile(repository, targetBranch, filePath, content, fileExists);
                
            case FAIL_IF_EXISTS:
                if (fileExists) {
                    throw new IllegalStateException("File already exists: " + filePath);
                }
                return createFile(repository, targetBranch, filePath, content);
                
            default:
                throw new IllegalArgumentException("Unknown file handling strategy: " + strategy);
        }
    }

    // Méthodes privées pour chaque stratégie
    private PushResult updateOrCreate(GHRepository repository, String branch, String filePath, 
                                     String content, boolean fileExists) throws IOException {
        if (fileExists) {
            var existingContent = repository.getFileContent(filePath, branch);
            GHContentUpdateResponse response = repository.createContent()
                    .path(filePath)
                    .content(content)
                    .branch(branch)
                    .message("Update CI workflow file")
                    .sha(existingContent.getSha())
                    .commit();
            return new PushResult(response.getCommit().getSha(), filePath, PushAction.UPDATED, "File updated successfully");
        } else {
            return createFile(repository, branch, filePath, content);
        }
    }

    private PushResult createNewFile(GHRepository repository, String branch, String originalFilePath, 
                                   String content, boolean fileExists) throws IOException {
        String finalFilePath = originalFilePath;
        
        if (fileExists) {
            // Générer un nouveau nom de fichier
            finalFilePath = generateUniqueFileName(repository, branch, originalFilePath);
        }
        
        GHContentUpdateResponse response = repository.createContent()
                .path(finalFilePath)
                .content(content)
                .branch(branch)
                .message("Add new CI workflow file")
                .commit();
        
        String message = fileExists ? 
            "New file created with unique name: " + finalFilePath : 
            "New file created: " + finalFilePath;
            
        return new PushResult(response.getCommit().getSha(), finalFilePath, PushAction.CREATED, message);
    }

    private PushResult createFile(GHRepository repository, String branch, String filePath, String content) throws IOException {
        GHContentUpdateResponse response = repository.createContent()
                .path(filePath)
                .content(content)
                .branch(branch)
                .message("Add CI workflow file")
                .commit();
        return new PushResult(response.getCommit().getSha(), filePath, PushAction.CREATED, "File created successfully");
    }

    private String generateUniqueFileName(GHRepository repository, String branch, String originalFilePath) throws IOException {
        String baseName = originalFilePath.substring(0, originalFilePath.lastIndexOf('.'));
        String extension = originalFilePath.substring(originalFilePath.lastIndexOf('.'));
        
        int counter = 1;
        String newFilePath;
        
        do {
            newFilePath = baseName + "-" + counter + extension;
            counter++;
        } while (fileExists(repository, branch, newFilePath));
        
        return newFilePath;
    }

    private boolean fileExists(GHRepository repository, String branch, String filePath) {
        try {
            repository.getFileContent(filePath, branch);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    
    public String pushWorkflowToGitHub(String token, String repoFullName, String branch, String filePath, String content) throws IOException {
        PushResult result = pushWorkflowToGitHub(token, repoFullName, branch, filePath, content, FileHandlingStrategy.UPDATE_IF_EXISTS);
        return result.getCommitHash();
    }

    
    public boolean fileExists(String token, String repoFullName, String branch, String filePath) throws IOException {
        try {
            GitHub github = GitHub.connectUsingOAuth(token);
            GHRepository repository = github.getRepository(repoFullName);
            String targetBranch = branch != null ? branch : "main";
            
            repository.getFileContent(filePath, targetBranch);
            return true;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Not Found")) {
                return false;
            }
            throw e;
        }
    }

    
    public static class PushResult {
        private final String commitHash;
        private final String filePath;
        private final PushAction action;
        private final String message;

        public PushResult(String commitHash, String filePath, PushAction action, String message) {
            this.commitHash = commitHash;
            this.filePath = filePath;
            this.action = action;
            this.message = message;
        }

        public String getCommitHash() { return commitHash; }
        public String getFilePath() { return filePath; }
        public PushAction getAction() { return action; }
        public String getMessage() { return message; }
    }

    public enum PushAction {
        CREATED, UPDATED, SKIPPED
    }

    public enum FileHandlingStrategy {
        UPDATE_IF_EXISTS,    // Mettre à jour si existe, créer sinon
        CREATE_NEW_ALWAYS,   // Toujours créer un nouveau fichier (avec suffix si nécessaire)
        FAIL_IF_EXISTS
    }
}