package com.example.demo.service;

import org.kohsuke.github.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class GitHubService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GITHUB_API_URL = "https://api.github.com";
    private static final String GITHUB_DIR = ".github";
    private static final String WORKFLOWS_DIR = ".github/workflows";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    public PushResult pushWorkflowToGitHub(
            String token,
            String repoFullName,
            String branch,
            String filePath,
            String content,
            FileHandlingStrategy strategy
    ) throws IOException {

        System.out.println("\n=== [pushWorkflowToGitHub] START ===");
        System.out.println("Token fourni: " + (token != null && !token.isEmpty()));
        System.out.println("Repo full name: " + repoFullName);
        System.out.println("Branche demandée: " + branch);
        System.out.println("Chemin du fichier: " + filePath);
        System.out.println("Stratégie demandée: " + strategy);

        // Validation des paramètres
        validateParameters(token, repoFullName, filePath, content);
        validateFilePath(filePath);

        // 🔧 NOUVELLE VALIDATION COMPLÈTE DES PERMISSIONS
        validateTokenPermissions(token, repoFullName);

        GitHub github = GitHub.connectUsingOAuth(token);
        GHRepository repository = github.getRepository(repoFullName);

        System.out.println("Repository info - Name: " + repository.getName() + ", Full name: " + repository.getFullName());
        
        String targetBranch = (branch != null && !branch.isBlank()) ? branch : repository.getDefaultBranch();
        System.out.println("Branche utilisée: " + targetBranch);

        // Vérifier que la branche existe
        if (!branchExists(repository, targetBranch)) {
            throw new IllegalArgumentException("Branch does not exist: " + targetBranch);
        }

        // 🔧 VALIDATION DES PERMISSIONS DU REPOSITORY
        validateRepositoryPermissions(repository);

        System.out.println("Repository info - Name: " + repository.getName() + 
                          ", Private: " + repository.isPrivate() + 
                          ", Owner: " + repository.getOwner().getLogin());

        // Création sécurisée des dossiers nécessaires
        ensureWorkflowsDirectoryExistsFixed(repository, targetBranch);

        // Application de la stratégie avec retry logic
        return executeWithRetry(() -> {
            GHRepository freshRepo = github.getRepository(repoFullName);
            return applyFileStrategy(freshRepo, targetBranch, filePath, content, strategy);
        });
    }

    // 🆕 VALIDATION DU CHEMIN DE FICHIER
    private void validateFilePath(String filePath) throws IOException {
        System.out.println("🔍 === VALIDATION CHEMIN FICHIER ===");
        System.out.println("Chemin fourni: " + filePath);
        
        if (filePath.startsWith("github/workflows/")) {
            String correctPath = "." + filePath;
            System.out.println("❌ ERREUR: Chemin incorrect détecté!");
            System.out.println("   Fourni: " + filePath);
            System.out.println("   Correct: " + correctPath);
            throw new IllegalArgumentException(
                "Incorrect file path. GitHub Actions requires '.github/workflows/' not 'github/workflows/'. " +
                "Expected: " + correctPath + ", but got: " + filePath
            );
        }
        
        if (!filePath.startsWith(".github/workflows/")) {
            System.out.println("⚠️ ATTENTION: Le chemin ne commence pas par '.github/workflows/'");
            System.out.println("   Chemin actuel: " + filePath);
            System.out.println("   Pour GitHub Actions, le chemin doit être: .github/workflows/filename.yml");
        }
        
        System.out.println("✅ Chemin validé: " + filePath);
    }

    private void validateTokenPermissions(String token, String repoFullName) throws IOException {
        System.out.println("🔐 === VALIDATION TOKEN PERMISSIONS ===");
        
        try {
            HttpHeaders headers = createHeaders(token);
            
            // 1. Vérifier les scopes du token
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> scopeResponse = restTemplate.exchange(
                GITHUB_API_URL + "/user", HttpMethod.GET, entity, String.class);
            
            String scopes = scopeResponse.getHeaders().getFirst("X-OAuth-Scopes");
            System.out.println("🎯 Token scopes: " + scopes);
            
            if (scopes == null || (!scopes.contains("repo") && !scopes.contains("public_repo"))) {
                throw new IllegalArgumentException("Token missing required scopes. Found: " + scopes + 
                    ". Required: 'repo' (for private repos) or 'public_repo' (for public repos)");
            }
            
            // 2. Vérifier l'accès au repository spécifique
            String repoApiUrl = GITHUB_API_URL + "/repos/" + repoFullName;
            ResponseEntity<Map> repoResponse = restTemplate.exchange(
                repoApiUrl, HttpMethod.GET, entity, Map.class);
                
            Map<String, Object> repoData = repoResponse.getBody();
            Map<String, Object> permissions = (Map<String, Object>) repoData.get("permissions");
            
            System.out.println("📋 Repository permissions:");
            System.out.println("  - Admin: " + permissions.get("admin"));
            System.out.println("  - Push: " + permissions.get("push"));
            System.out.println("  - Pull: " + permissions.get("pull"));
            
            Boolean canPush = (Boolean) permissions.get("push");
            if (!Boolean.TRUE.equals(canPush)) {
                throw new IllegalArgumentException("Token does not have push permissions to repository: " + repoFullName);
            }
            
            System.out.println("✅ Token permissions validées");
            
        } catch (RestClientException e) {
            System.out.println("❌ Erreur validation token: " + e.getMessage());
            throw new IOException("Token validation failed: " + e.getMessage(), e);
        }
    }

    // 🆕 VALIDATION PERMISSIONS REPOSITORY
    private void validateRepositoryPermissions(GHRepository repository) throws IOException {
        System.out.println("🏛️ === VALIDATION REPOSITORY PERMISSIONS ===");
        
        try {
            // Vérifier les permissions détaillées
            GHPermissionType permission = repository.getPermission(repository.getOwner());
            System.out.println("🔑 Permission type: " + permission);
            
            if (permission == GHPermissionType.READ) {
                throw new IllegalArgumentException("Insufficient permissions: READ only. WRITE or ADMIN required.");
            }
            
            // Tester l'accès en écriture avec un test simple
            System.out.println("🧪 Test d'accès en écriture...");
            
            // Essayer de lire le contenu du repository pour vérifier l'accès
            try {
                repository.getFileContent("README.md");
                System.out.println("✅ Accès en lecture confirmé");
            } catch (Exception e) {
                System.out.println("⚠️ Pas de README.md (normal)");
            }
            
            System.out.println("✅ Repository permissions validées");
            
        } catch (IOException e) {
            System.out.println("❌ Erreur validation repository: " + e.getMessage());
            throw new IOException("Repository permission validation failed: " + e.getMessage(), e);
        }
    }

    private void validateParameters(String token, String repoFullName, String filePath, String content) {
        if (token == null || token.isEmpty())
            throw new IllegalArgumentException("GitHub token cannot be null or empty");
        if (repoFullName == null || repoFullName.isEmpty())
            throw new IllegalArgumentException("Repository full name cannot be null or empty");
        if (filePath == null || filePath.isEmpty())
            throw new IllegalArgumentException("File path cannot be null or empty");
        if (content == null)
            throw new IllegalArgumentException("Content cannot be null");
    }

    private boolean branchExists(GHRepository repository, String branchName) {
        try {
            GHBranch branch = repository.getBranch(branchName);
            System.out.println("✅ Branche trouvée: " + branchName + " (SHA: " + branch.getSHA1() + ")");
            return true;
        } catch (IOException e) {
            System.out.println("⚠ Branche introuvable: " + branchName + " - " + e.getMessage());
            
            try {
                System.out.println("Branches disponibles:");
                repository.getBranches().forEach((name, ghBranch) -> 
                    System.out.println("  - " + name));
            } catch (IOException ex) {
                System.out.println("Impossible de lister les branches: " + ex.getMessage());
            }
            
            return false;
        }
    }

    private void ensureWorkflowsDirectoryExistsFixed(GHRepository repository, String branch) throws IOException {
        System.out.println("🔧 [FIXED] Création forcée des dossiers sur branche: " + branch);
        
        try {
            // 1. Vérifier/créer le dossier .github
            if (!directoryExistsRobust(repository, branch, GITHUB_DIR)) {
                System.out.println("📁 Création du dossier .github...");
                createDirectoryWithPlaceholder(repository, branch, GITHUB_DIR);
                waitForPropagation();
            } else {
                System.out.println("✅ Dossier .github existe déjà");
            }

            // 2. Vérifier/créer le dossier .github/workflows
            if (!directoryExistsRobust(repository, branch, WORKFLOWS_DIR)) {
                System.out.println("📁 Création du dossier .github/workflows...");
                createDirectoryWithPlaceholder(repository, branch, WORKFLOWS_DIR);
                waitForPropagation();
            } else {
                System.out.println("✅ Dossier .github/workflows existe déjà");
            }

            // 3. Vérification finale
            boolean workflowsExists = directoryExistsRobust(repository, branch, WORKFLOWS_DIR);
            System.out.println("🎯 Vérification finale - Dossier workflows existe: " + workflowsExists);
            
            if (!workflowsExists) {
                throw new IOException("Impossible de créer ou vérifier le dossier .github/workflows");
            }

        } catch (IOException e) {
            System.out.println("❌ Erreur lors de la création des dossiers: " + e.getMessage());
            throw new IOException("Failed to ensure workflows directory exists: " + e.getMessage(), e);
        }
    }

    private void createDirectoryWithPlaceholder(GHRepository repository, String branch, String dirPath) throws IOException {
        try {
            String placeholderPath = dirPath + "/.gitkeep";
            String placeholderContent = "# Directory placeholder - ensures " + dirPath + " exists\n# Created automatically by CI/CD Management App";
            
            System.out.println("📝 Création du placeholder: " + placeholderPath);
            
            GHContentUpdateResponse response = repository.createContent()
                    .path(placeholderPath)
                    .content(placeholderContent)
                    .branch(branch)
                    .message("Create " + dirPath + " directory structure")
                    .commit();
                    
            System.out.println("✅ Dossier " + dirPath + " créé avec succès - Commit: " + response.getCommit().getSHA1());
            
        } catch (IOException e) {
            if (isAlreadyExistsError(e)) {
                System.out.println("✅ Dossier " + dirPath + " existe déjà (normal)");
            } else {
                System.out.println("❌ Erreur création dossier " + dirPath + ": " + e.getMessage());
                throw e;
            }
        }
    }

    private PushResult executeWithRetry(RetryableOperation operation) throws IOException {
        IOException lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                System.out.println("🔄 Tentative " + attempt + "/" + MAX_RETRIES);
                return operation.execute();
            } catch (IOException e) {
                lastException = e;
                System.out.println("❌ Tentative " + attempt + " échouée: " + e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    System.out.println("⏳ Attente avant retry: " + RETRY_DELAY_MS + "ms");
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        throw new IOException("Operation failed after " + MAX_RETRIES + " attempts", lastException);
    }

    private PushResult applyFileStrategy(
            GHRepository repository, 
            String branch, 
            String filePath, 
            String content, 
            FileHandlingStrategy strategy
    ) throws IOException {
        
        switch (strategy) {
            case UPDATE_IF_EXISTS:
                if (fileExists(repository, branch, filePath)) {
                    return updateFile(repository, branch, filePath, content);
                } else {
                    return createFile(repository, branch, filePath, content);
                }

            case CREATE_NEW_ALWAYS:
                if (fileExists(repository, branch, filePath)) {
                    String uniqueFilePath = generateUniqueFilePathWithIncrement(repository, branch, filePath);
                    System.out.println("[CREATE_NEW_ALWAYS] Fichier existe → nouveau nom: " + uniqueFilePath);
                    return createFile(repository, branch, uniqueFilePath, content,
                            "New workflow created with incremental name");
                } else {
                    return createFile(repository, branch, filePath, content);
                }

            case FAIL_IF_EXISTS:
                if (fileExists(repository, branch, filePath)) {
                    throw new IllegalStateException("File already exists: " + filePath);
                }
                return createFile(repository, branch, filePath, content);

            default:
                throw new IllegalArgumentException("Unknown file handling strategy: " + strategy);
        }
    }

    // 🆕 GÉNÉRATION D'UN NOM UNIQUE AVEC INCRÉMENTATION NUMÉRIQUE
    private String generateUniqueFilePathWithIncrement(GHRepository repository, String branch, String originalFilePath) throws IOException {
        System.out.println("🔢 === GÉNÉRATION NOM AVEC INCRÉMENTATION ===");
        System.out.println("Fichier original: " + originalFilePath);
        
        // Extraire le nom de base et l'extension
        int lastDotIndex = originalFilePath.lastIndexOf('.');
        String baseName;
        String extension;
        
        if (lastDotIndex == -1) {
            baseName = originalFilePath;
            extension = "";
        } else {
            baseName = originalFilePath.substring(0, lastDotIndex);
            extension = originalFilePath.substring(lastDotIndex);
        }
        
        System.out.println("Nom de base: " + baseName);
        System.out.println("Extension: " + extension);
        
        // Rechercher le prochain numéro disponible
        int counter = 1;
        String newFilePath;
        
        while (true) {
            newFilePath = baseName + "-" + counter + extension;
            System.out.println("🔍 Test existence: " + newFilePath);
            
            if (!fileExists(repository, branch, newFilePath)) {
                System.out.println("✅ Nom unique trouvé: " + newFilePath + " (compteur: " + counter + ")");
                break;
            }
            
            System.out.println("❌ " + newFilePath + " existe déjà, incrémentation...");
            counter++;
            
            // Protection contre les boucles infinies
            if (counter > 1000) {
                throw new IOException("Unable to generate unique filename after 1000 attempts for: " + originalFilePath);
            }
        }
        
        return newFilePath;
    }

    private boolean directoryExistsRobust(GHRepository repository, String branch, String dirPath) {
        try {
            System.out.println("🔍 Vérification existence dossier: " + dirPath + " sur branche: " + branch);
            
            List<GHContent> contents = repository.getDirectoryContent(dirPath, branch);
            boolean exists = contents != null && !contents.isEmpty();
            
            System.out.println("Résultat vérification " + dirPath + ": " + exists + 
                             (exists ? " (" + contents.size() + " éléments)" : ""));
            
            return exists;
        } catch (IOException e) {
            if (e.getMessage().contains("404") || e.getMessage().contains("Not Found")) {
                System.out.println("❌ Dossier " + dirPath + " n'existe pas (404)");
                return false;
            } else {
                System.out.println("⚠ Erreur lors de la vérification du dossier " + dirPath + ": " + e.getMessage());
                return false;
            }
        }
    }

    private void waitForPropagation() {
        try {
            System.out.println("⏳ Attente propagation GitHub (2s)...");
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private PushResult updateFile(GHRepository repository, String branch, String filePath, String content) throws IOException {
        System.out.println("🔄 Mise à jour du fichier: " + filePath + " sur branche: " + branch);
        
        try {
            GHContent existingContent = repository.getFileContent(filePath, branch);
            
            String existingContentStr = existingContent.getContent();
            if (content.equals(existingContentStr)) {
                System.out.println("📝 Contenu identique - pas de mise à jour nécessaire");
                return new PushResult(null, filePath, PushAction.SKIPPED, "Content unchanged");
            }

            GHContentUpdateResponse response = repository.createContent()
                    .path(filePath)
                    .content(content)
                    .branch(branch)
                    .message("Update workflow " + extractFileName(filePath))
                    .sha(existingContent.getSha())
                    .commit();

            System.out.println("✅ Fichier mis à jour - Commit: " + response.getCommit().getSHA1());
            return new PushResult(response.getCommit().getSHA1(), filePath, PushAction.UPDATED, "File updated successfully");
            
        } catch (IOException e) {
            System.out.println("❌ Erreur lors de la mise à jour: " + e.getMessage());
            throw new IOException("Failed to update file " + filePath + ": " + e.getMessage(), e);
        }
    }

    private PushResult createFile(GHRepository repository, String branch, String filePath, String content) throws IOException {
        return createFile(repository, branch, filePath, content, "Add workflow " + extractFileName(filePath));
    }

    // 🔧 CRÉATION DE FICHIER AVEC MEILLEURE GESTION D'ERREURS
    private PushResult createFile(GHRepository repository, String branch, String filePath, String content, String commitMessage) throws IOException {
        System.out.println("🆕 Création du fichier: " + filePath + " sur branche: " + branch);
        
        try {
            System.out.println("📋 Détails création - Taille contenu: " + content.length() + " caractères");
            
            // 🆕 TENTATIVE ALTERNATIVE SI L'API ÉCHOUE
            try {
                GHContentUpdateResponse response = repository.createContent()
                        .path(filePath)
                        .content(content)
                        .branch(branch)
                        .message(commitMessage)
                        .commit();
                        
                System.out.println("✅ Fichier créé avec succès - Commit: " + response.getCommit().getSHA1());
                return new PushResult(response.getCommit().getSHA1(), filePath, PushAction.CREATED, "File created successfully");
                
            } catch (IOException apiError) {
                System.out.println("🔄 Échec API standard, tentative avec REST API directe...");
                return createFileViaRestApi(repository.getFullName(), repository.getOwner().getLogin(), branch, filePath, content, commitMessage);
            }
            
        } catch (IOException e) {
            System.out.println("❌ Erreur lors de la création du fichier: " + e.getMessage());
            
            System.out.println("🔍 Analyse de l'erreur:");
            System.out.println("  - Repository: " + repository.getFullName());
            System.out.println("  - Branch: " + branch);
            System.out.println("  - File path: " + filePath);
            System.out.println("  - Error type: " + e.getClass().getSimpleName());
            
            throw new IOException("Failed to create file " + filePath + ": " + e.getMessage(), e);
        }
    }

    // 🆕 CRÉATION VIA REST API DIRECTE (FALLBACK)
    private PushResult createFileViaRestApi(String repoFullName, String owner, String branch, String filePath, String content, String commitMessage) throws IOException {
        try {
            System.out.println("🌐 Utilisation REST API directe pour: " + filePath);
            
            String apiUrl = GITHUB_API_URL + "/repos/" + repoFullName + "/contents/" + filePath;
            HttpHeaders headers = createHeaders(getCurrentToken());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("message", commitMessage);
            requestBody.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
            requestBody.put("branch", branch);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.PUT, entity, Map.class);
            
            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> commitInfo = (Map<String, Object>) responseBody.get("commit");
            String commitSha = (String) commitInfo.get("sha");
            
            System.out.println("✅ Fichier créé via REST API - Commit: " + commitSha);
            return new PushResult(commitSha, filePath, PushAction.CREATED, "File created via REST API");
            
        } catch (Exception e) {
            System.out.println("❌ Échec REST API également: " + e.getMessage());
            throw new IOException("Both GitHub API and REST API failed: " + e.getMessage(), e);
        }
    }

    // Variable pour stocker le token actuel
    private String currentToken;
    
    public void setCurrentToken(String token) {
        this.currentToken = token;
    }

    private String getCurrentToken() {return currentToken;}
    private String extractFileName(String filePath) {
        return filePath.substring(filePath.lastIndexOf('/') + 1);
    }

    private boolean isAlreadyExistsError(IOException e) {
        String message = e.getMessage();
        return message != null && (
            message.contains("already exists") || 
            message.contains("422") || 
            message.contains("name already exists on this branch")
        );
    }

    // 🗑️ MÉTHODE DÉPRÉCIÉE - Remplacée par generateUniqueFilePathWithIncrement
    @Deprecated
    private String generateUniqueFileNameSafe(String originalFilePath) {
        int lastDotIndex = originalFilePath.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return originalFilePath + "-" + System.currentTimeMillis();
        }
        
        String baseName = originalFilePath.substring(0, lastDotIndex);
        String extension = originalFilePath.substring(lastDotIndex);
        return baseName + "-" + System.currentTimeMillis() + extension;
    }

    private boolean fileExists(GHRepository repository, String branch, String filePath) {
        try {
            System.out.println("🔍 Vérification existence fichier: " + filePath + " sur branche: " + branch);
            repository.getFileContent(filePath, branch);
            System.out.println("✅ Fichier existe: " + filePath);
            return true;
        } catch (IOException e) {
            System.out.println("❌ Fichier n'existe pas: " + filePath + " (" + e.getMessage() + ")");
            return false;
        }
    }

    @FunctionalInterface
    private interface RetryableOperation {
        PushResult execute() throws IOException;
    }

    // === REST API helpers ===

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
            return new String(Base64.getDecoder().decode(encodedContent.replaceAll("\\s", "")), StandardCharsets.UTF_8);
        } catch (RestClientException e) {
            throw new RuntimeException("Error while retrieving file: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getUserRepositories(String token) {
        HttpHeaders headers = createHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<List> response = restTemplate.exchange(
                GITHUB_API_URL + "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator",
                HttpMethod.GET, entity, List.class);
        return response.getBody();
    }

    public Map<String, Object> getUserInfo(String token) {
        HttpHeaders headers = createHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                GITHUB_API_URL + "/user", HttpMethod.GET, entity, Map.class);
        return response.getBody();
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