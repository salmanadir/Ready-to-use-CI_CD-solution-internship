package com.example.demo.service;

import org.kohsuke.github.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

@Service
public class GitHubService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GITHUB_API_URL = "https://api.github.com";
    private static final String WORKFLOWS_DIR = ".github/workflows";

    public PushResult pushWorkflowToGitHub(
            String token,
            String repoFullName,
            String branch,
            String filePath,
            String content,
            FileHandlingStrategy strategy
    ) throws IOException {

        if (token == null || token.isEmpty())
            throw new IllegalArgumentException("GitHub token cannot be null or empty");
        if (repoFullName == null || repoFullName.isEmpty())
            throw new IllegalArgumentException("Repository full name cannot be null or empty");
        if (filePath == null || filePath.isEmpty())
            throw new IllegalArgumentException("File path cannot be null or empty");
        if (content == null)
            throw new IllegalArgumentException("Content cannot be null");

        GitHub github = GitHub.connectUsingOAuth(token);
        
        // 🔧 Validation du repository et de la branche
        GHRepository repository;
        try {
            repository = github.getRepository(repoFullName);
            System.out.println("Repository trouvé: " + repository.getFullName());
        } catch (GHFileNotFoundException e) {
            throw new IllegalArgumentException("Repository not found: " + repoFullName + ". Vérifiez que le nom du repository est correct.");
        }

        String targetBranch = (branch != null && !branch.isBlank()) ? branch : "main";
        
        // 🔧 Validation de l'existence de la branche
        try {
            GHBranch branchObj = repository.getBranch(targetBranch);
            System.out.println("Branche trouvée: " + branchObj.getName());
        } catch (GHFileNotFoundException e) {
            // Essayer avec 'master' si 'main' n'existe pas
            if (targetBranch.equals("main")) {
                try {
                    targetBranch = "master";
                    GHBranch masterBranch = repository.getBranch(targetBranch);
                    System.out.println("Branche 'main' non trouvée, utilisation de 'master': " + masterBranch.getName());
                } catch (GHFileNotFoundException ex) {
                    throw new IllegalArgumentException("Aucune branche 'main' ou 'master' trouvée dans le repository: " + repoFullName);
                }
            } else {
                throw new IllegalArgumentException("Branch not found: " + targetBranch + " in repository: " + repoFullName);
            }
        }

        // 🔧 Vérification des permissions
        try {
            GHPermissionType permission = repository.getPermission(github.getMyself());
            System.out.println("Permission sur le repository: " + permission);
            if (permission != GHPermissionType.ADMIN && permission != GHPermissionType.WRITE) {
                throw new IllegalArgumentException("Insufficient permissions on repository. Required: WRITE or ADMIN, Current: " + permission);
            }
        } catch (IOException e) {
            System.err.println("Impossible de vérifier les permissions: " + e.getMessage());
        }

        System.out.println("Création des dossiers nécessaires...");
        // 1️⃣ Créer les dossiers .github et .github/workflows s'ils n'existent pas
        ensureWorkflowsDirectoryExists(repository, targetBranch);

        // 2️⃣ Appliquer la stratégie (sans vérification préalable problématique)
        System.out.println("Application de la stratégie: " + strategy);

        switch (strategy) {
            case UPDATE_IF_EXISTS:
                try {
                    // Essayer de récupérer le fichier existant pour mise à jour
                    return updateFile(repository, targetBranch, filePath, content);
                } catch (GHFileNotFoundException e) {
                    // Le fichier n'existe pas, le créer
                    System.out.println("Fichier non trouvé, création d'un nouveau fichier");
                    return createFile(repository, targetBranch, filePath, content);
                } catch (IOException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Not Found")) {
                        System.out.println("Fichier non trouvé (404), création d'un nouveau fichier");
                        return createFile(repository, targetBranch, filePath, content);
                    }
                    throw e; // Re-throw pour autres erreurs
                }

            case CREATE_NEW_ALWAYS:
                try {
                    // Essayer de créer directement
                    return createFile(repository, targetBranch, filePath, content);
                } catch (IOException e) {
                    if (e.getMessage() != null && (e.getMessage().contains("already exists") || 
                                                  e.getMessage().contains("422"))) {
                        // Le fichier existe déjà, créer avec un nom unique
                        System.out.println("Fichier existe déjà, création avec nom unique");
                        String uniqueFilePath = generateUniqueFileNameSafe(repository, targetBranch, filePath);
                        return createFile(repository, targetBranch, uniqueFilePath, content,
                                "New workflow created with unique name");
                    }
                    throw e; // Re-throw pour autres erreurs
                }

            case FAIL_IF_EXISTS:
                try {
                    // Essayer de créer directement
                    return createFile(repository, targetBranch, filePath, content);
                } catch (IOException e) {
                    if (e.getMessage() != null && (e.getMessage().contains("already exists") || 
                                                  e.getMessage().contains("422"))) {
                        // Le fichier existe déjà, lancer une exception comme demandé
                        throw new IllegalStateException("File already exists: " + filePath);
                    }
                    throw e; // Re-throw pour autres erreurs
                }

            default:
                throw new IllegalArgumentException("Unknown file handling strategy: " + strategy);
        }
    }

    /**
     * 🔧 Version améliorée de la création du dossier workflows
     */
    private void ensureWorkflowsDirectoryExists(GHRepository repository, String branch) throws IOException {
        System.out.println("Vérification existence du dossier .github...");
        
        // 🔧 Approche plus robuste : créer directement le fichier workflow
        // Si les dossiers n'existent pas, GitHub les créera automatiquement
        try {
            // Tenter de lister le contenu du dossier workflows pour voir s'il existe
            List<GHContent> workflowsContent = repository.getDirectoryContent(WORKFLOWS_DIR, branch);
            System.out.println("Dossier .github/workflows existe déjà avec " + workflowsContent.size() + " fichiers");
        } catch (GHFileNotFoundException e) {
            System.out.println("Dossier .github/workflows n'existe pas encore, sera créé automatiquement");
            // GitHub créera automatiquement les dossiers lors de la création du fichier
        }
    }

    /**
     * 🔧 Version améliorée avec plus de logging et gestion d'erreurs
     */
    private PushResult updateFile(GHRepository repository, String branch, String filePath, String content) throws IOException {
        System.out.println("Tentative de mise à jour du fichier: " + filePath);
        try {
            GHContent existingContent = repository.getFileContent(filePath, branch);
            System.out.println("Fichier existant trouvé, SHA: " + existingContent.getSha());
            
            GHContentUpdateResponse response = repository.createContent()
                    .path(filePath)
                    .content(content)
                    .branch(branch)
                    .message("Update CI workflow file")
                    .sha(existingContent.getSha())
                    .commit();
            
            System.out.println("Fichier mis à jour avec succès");
            return new PushResult(response.getCommit().getSHA1(), filePath, PushAction.UPDATED, "File updated successfully");
        } catch (GHFileNotFoundException e) {
            System.out.println("Fichier non trouvé pour mise à jour, tentative de création");
            throw e; // Re-throw pour que la logique appelante puisse créer le fichier
        }
    }

    private PushResult createFile(GHRepository repository, String branch, String filePath, String content) throws IOException {
        return createFile(repository, branch, filePath, content, "Create CI workflow file");
    }

    /**
     * 🔧 Version améliorée avec plus de logging et gestion d'erreurs spécifiques
     */
    private PushResult createFile(GHRepository repository, String branch, String filePath, String content, String commitMessage) throws IOException {
        System.out.println("=== CRÉATION FICHIER ===");
        System.out.println("Repository: " + repository.getFullName());
        System.out.println("Branch: " + branch);
        System.out.println("File Path: " + filePath);
        System.out.println("Content Length: " + content.length());
        System.out.println("Commit Message: " + commitMessage);
        
        try {
            // 🔧 Validation supplémentaire avant création
            if (content.trim().isEmpty()) {
                throw new IllegalArgumentException("Content cannot be empty");
            }

            GHContentUpdateResponse response = repository.createContent()
                    .path(filePath)
                    .content(content)
                    .branch(branch)
                    .message(commitMessage)
                    .commit();
            
            System.out.println("✅ Fichier créé avec succès - Commit: " + response.getCommit().getSHA1());
            return new PushResult(response.getCommit().getSHA1(), filePath, PushAction.CREATED, "File created successfully");
            
        } catch (IOException e) {
            System.err.println("❌ Erreur lors de la création du fichier:");
            System.err.println("Message: " + e.getMessage());
            System.err.println("Class: " + e.getClass().getSimpleName());
            
            // 🔧 Analyse spécifique des erreurs courantes
            if (e.getMessage() != null) {
                if (e.getMessage().contains("Not Found")) {
                    throw new IOException("Repository ou branche introuvable. Vérifiez que le repository '" + 
                                        repository.getFullName() + "' existe et que la branche '" + branch + "' est correcte.", e);
                } else if (e.getMessage().contains("Bad credentials")) {
                    throw new IOException("Token GitHub invalide ou insuffisant. Vérifiez que le token a les permissions 'repo'.", e);
                } else if (e.getMessage().contains("403")) {
                    throw new IOException("Permissions insuffisantes. Le token doit avoir les permissions 'repo' sur ce repository.", e);
                } else if (e.getMessage().contains("422")) {
                    throw new IOException("Erreur de validation GitHub. Le fichier existe peut-être déjà.", e);
                }
            }
            throw e;
        }
    }

    /**
     * Version sécurisée de generateUniqueFileName qui évite les appels API problématiques
     */
    private String generateUniqueFileNameSafe(GHRepository repository, String branch, String originalFilePath) throws IOException {
        String baseName = originalFilePath.substring(0, originalFilePath.lastIndexOf('.'));
        String extension = originalFilePath.substring(originalFilePath.lastIndexOf('.'));
        
        // Utiliser timestamp + random pour éviter les conflits
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 1000);
        String uniqueFilePath = baseName + "-" + timestamp + "-" + random + extension;
        
        System.out.println("Génération nom unique: " + originalFilePath + " -> " + uniqueFilePath);
        return uniqueFilePath;
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
        } catch (GHFileNotFoundException e) {
            return false;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Not Found")) {
                return false;
            }
            System.err.println("Erreur lors de la vérification de l'existence du fichier: " + e.getMessage());
            return false;
        }
    }

    // 🔧 Méthode utilitaire pour tester la connectivité GitHub
    public boolean testGitHubConnection(String token, String repoFullName) {
        try {
            GitHub github = GitHub.connectUsingOAuth(token);
            GHRepository repository = github.getRepository(repoFullName);
            GHUser user = github.getMyself();
            
            System.out.println("✅ Test de connexion GitHub réussi:");
            System.out.println("  - User: " + user.getLogin());
            System.out.println("  - Repository: " + repository.getFullName());
            System.out.println("  - Permissions: " + repository.getPermission(user));
            
            return true;
        } catch (Exception e) {
            System.err.println("❌ Test de connexion GitHub échoué: " + e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> getRepositoryContents(String repoUrl, String token, String path) {
        String apiUrl = repoUrl.replace("https://github.com", GITHUB_API_URL + "/repos") 
                      + "/contents/" + (path != null ? path : "");
    
        HttpHeaders headers = createHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
    
        try {
            ResponseEntity<List> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, List.class);
            return response.getBody();
        } catch (RestClientException e) {
            throw new RuntimeException("Error while retrieving files: " + e.getMessage());
        }
    }
    
    public String getFileContent(String repoUrl, String token, String filePath) {
        String apiUrl = repoUrl.replace("https://github.com", GITHUB_API_URL + "/repos") + "/contents/" + filePath;
    
        HttpHeaders headers = createHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
    
        try {
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, Map.class);
            Map<String, Object> fileData = response.getBody();
    
            String encodedContent = (String) fileData.get("content");
            return new String(Base64.getDecoder().decode(encodedContent.replaceAll("\\s", "")));
        } catch (RestClientException e) {
            throw new RuntimeException("Error while retrieving file: " + e.getMessage());
        }
    }
    
    // REST API Helpers
    public List<Map<String, Object>> getUserRepositories(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new RuntimeException("GitHub token is required");
        }
        HttpHeaders headers = createHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    GITHUB_API_URL + "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator",
                    HttpMethod.GET,
                    entity,
                    List.class
            );
            return response.getBody();
        } catch (RestClientException e) {
            throw new RuntimeException("Error while fetching repositories: " + e.getMessage());
        }
    }

    public Map<String, Object> getUserInfo(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new RuntimeException("GitHub token is required");
        }
        HttpHeaders headers = createHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    GITHUB_API_URL + "/user",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            return response.getBody();
        } catch (RestClientException e) {
            throw new RuntimeException("Error while fetching user information: " + e.getMessage());
        }
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "CI-CD-Management-App");
        return headers;
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

    public enum PushAction { CREATED, UPDATED, SKIPPED }
    public enum FileHandlingStrategy { UPDATE_IF_EXISTS, CREATE_NEW_ALWAYS, FAIL_IF_EXISTS }
}