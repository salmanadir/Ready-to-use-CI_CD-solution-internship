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

        validateParameters(token, repoFullName, filePath, content);
        validateFilePath(filePath);
        validateTokenPermissions(token, repoFullName);

        GitHub github = GitHub.connectUsingOAuth(token);
        GHRepository repository = github.getRepository(repoFullName);

        String targetBranch = (branch != null && !branch.isBlank()) ? branch : repository.getDefaultBranch();
        if (!branchExists(repository, targetBranch)) {
            throw new IllegalArgumentException("Branch does not exist: " + targetBranch);
        }

        validateRepositoryPermissions(repository);
        ensureWorkflowsDirectoryExistsFixed(repository, targetBranch);

        return executeWithRetry(() -> {
            GHRepository freshRepo = github.getRepository(repoFullName);
            return applyFileStrategy(freshRepo, targetBranch, filePath, content, strategy);
        });
    }

    private void validateFilePath(String filePath) {
        System.out.println("🔍 === VALIDATION CHEMIN FICHIER ===");
        if (filePath.startsWith("github/workflows/")) {
            String correctPath = "." + filePath;
            throw new IllegalArgumentException(
                "Incorrect file path. GitHub Actions requires '.github/workflows/' not 'github/workflows/'. " +
                "Expected: " + correctPath + ", but got: " + filePath
            );
        }
        if (!filePath.startsWith(".github/workflows/")) {
            System.out.println("⚠️ ATTENTION: chemin non standard pour GitHub Actions: " + filePath);
        }
        System.out.println("✅ Chemin validé: " + filePath);
    }

    private void validateTokenPermissions(String token, String repoFullName) throws IOException {
        System.out.println("🔐 === VALIDATION TOKEN PERMISSIONS ===");
        try {
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> scopeResponse = restTemplate.exchange(
                GITHUB_API_URL + "/user", HttpMethod.GET, entity, String.class);
            String scopes = scopeResponse.getHeaders().getFirst("X-OAuth-Scopes");
            if (scopes == null || (!scopes.contains("repo") && !scopes.contains("public_repo"))) {
                throw new IllegalArgumentException("Token missing required scopes. Found: " + scopes +
                        ". Required: 'repo' (private) or 'public_repo' (public).");
            }

            String repoApiUrl = GITHUB_API_URL + "/repos/" + repoFullName;
            ResponseEntity<Map> repoResponse = restTemplate.exchange(repoApiUrl, HttpMethod.GET, entity, Map.class);
            Map<String, Object> repoData = repoResponse.getBody();
            Map<String, Object> permissions = (Map<String, Object>) repoData.get("permissions");

            Boolean canPush = (Boolean) permissions.get("push");
            if (!Boolean.TRUE.equals(canPush)) {
                throw new IllegalArgumentException("Token does not have push permissions to repository: " + repoFullName);
            }
        } catch (RestClientException e) {
            throw new IOException("Token validation failed: " + e.getMessage(), e);
        }
    }

    private void validateRepositoryPermissions(GHRepository repository) throws IOException {
        System.out.println("🏛️ === VALIDATION REPOSITORY PERMISSIONS ===");
        try {
            GHPermissionType permission = repository.getPermission(repository.getOwner());
            if (permission == GHPermissionType.READ) {
                throw new IllegalArgumentException("Insufficient permissions: READ only. WRITE or ADMIN required.");
            }
            try { repository.getFileContent("README.md"); } catch (Exception ignored) {}
        } catch (IOException e) {
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
            repository.getBranch(branchName);
            return true;
        } catch (IOException e) {
            try { repository.getBranches().forEach((name, ghBranch) -> System.out.println("  - " + name)); }
            catch (IOException ignored) {}
            return false;
        }
    }

    private void ensureWorkflowsDirectoryExistsFixed(GHRepository repository, String branch) throws IOException {
        try {
            if (!directoryExistsRobust(repository, branch, GITHUB_DIR)) {
                createDirectoryWithPlaceholder(repository, branch, GITHUB_DIR);
                waitForPropagation();
            }
            if (!directoryExistsRobust(repository, branch, WORKFLOWS_DIR)) {
                createDirectoryWithPlaceholder(repository, branch, WORKFLOWS_DIR);
                waitForPropagation();
            }
            if (!directoryExistsRobust(repository, branch, WORKFLOWS_DIR)) {
                throw new IOException("Unable to create/verify .github/workflows");
            }
        } catch (IOException e) {
            throw new IOException("Failed to ensure workflows directory exists: " + e.getMessage(), e);
        }
    }

    private void createDirectoryWithPlaceholder(GHRepository repository, String branch, String dirPath) throws IOException {
        try {
            String placeholderPath = dirPath + "/.gitkeep";
            String placeholderContent = "# Directory placeholder for " + dirPath + "\n";
            GHContentUpdateResponse response = repository.createContent()
                    .path(placeholderPath)
                    .content(placeholderContent)
                    .branch(branch)
                    .message("Create " + dirPath + " directory structure")
                    .commit();
            System.out.println("✅ Created " + dirPath + " commit=" + response.getCommit().getSHA1());
        } catch (IOException e) {
            if (!isAlreadyExistsError(e)) throw e;
        }
    }

    private PushResult executeWithRetry(RetryableOperation operation) throws IOException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try { return operation.execute(); }
            catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
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

    private String generateUniqueFilePathWithIncrement(GHRepository repository, String branch, String originalFilePath) throws IOException {
        int lastDotIndex = originalFilePath.lastIndexOf('.');
        String baseName = (lastDotIndex == -1) ? originalFilePath : originalFilePath.substring(0, lastDotIndex);
        String extension = (lastDotIndex == -1) ? "" : originalFilePath.substring(lastDotIndex);
        int counter = 1;
        while (true) {
            String newFilePath = baseName + "-" + counter + extension;
            if (!fileExists(repository, branch, newFilePath)) return newFilePath;
            counter++;
            if (counter > 1000) throw new IOException("Unable to generate unique filename after 1000 attempts");
        }
    }

    private boolean directoryExistsRobust(GHRepository repository, String branch, String dirPath) {
        try {
            List<GHContent> contents = repository.getDirectoryContent(dirPath, branch);
            return contents != null && !contents.isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private void waitForPropagation() {
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // updateFile(...)
private PushResult updateFile(GHRepository repository, String branch, String filePath, String content) throws IOException {
    try {
        GHContent existingContent = repository.getFileContent(filePath, branch);
        String existing = existingContent.getContent();
        if (content.equals(existing)) {
            return new PushResult(null, filePath, PushAction.SKIPPED, "Content unchanged",
                    null, existingContent.getSha());
        }
        GHContentUpdateResponse response = repository.createContent()
                .path(filePath)
                .content(content)
                .branch(branch)
                .message("Update " + extractFileName(filePath))
                .sha(existingContent.getSha())
                .commit();

        String commitUrl = response.getCommit() != null ? response.getCommit().getHtmlUrl() : null;
        return new PushResult(response.getCommit().getSHA1(), filePath, PushAction.UPDATED, "File updated successfully",
                commitUrl, existingContent.getSha());
    } catch (IOException e) {
        throw new IOException("Failed to update file " + filePath + ": " + e.getMessage(), e);
    }
}


    private PushResult createFile(GHRepository repository, String branch, String filePath, String content) throws IOException {
        return createFile(repository, branch, filePath, content, "Add " + extractFileName(filePath));
    }

    // createFile(...)
private PushResult createFile(GHRepository repository, String branch, String filePath, String content, String commitMessage) throws IOException {
    try {
        GHContentUpdateResponse response = repository.createContent()
                .path(filePath)
                .content(content)
                .branch(branch)
                .message(commitMessage)
                .commit();

        String commitUrl = response.getCommit() != null ? response.getCommit().getHtmlUrl() : null;
        return new PushResult(response.getCommit().getSHA1(), filePath, PushAction.CREATED, "File created successfully",
                commitUrl, null);
    } catch (IOException apiError) {
        return createFileViaRestApi(repository.getFullName(), repository.getOwner().getLogin(), branch, filePath, content, commitMessage);
    }
}


    // createFileViaRestApi(...)
private PushResult createFileViaRestApi(String repoFullName, String owner, String branch, String filePath, String content, String commitMessage) throws IOException {
    try {
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
        String commitUrl = commitInfo.get("html_url") != null ? String.valueOf(commitInfo.get("html_url")) : null;

        return new PushResult(commitSha, filePath, PushAction.CREATED, "File created via REST API",
                commitUrl, null);
    } catch (Exception e) {
        throw new IOException("Both GitHub API and REST API failed: " + e.getMessage(), e);
    }
}


    private String currentToken;
    public void setCurrentToken(String token) { this.currentToken = token; }
    private String getCurrentToken() { return currentToken; }
    private String extractFileName(String filePath) { return filePath.substring(filePath.lastIndexOf('/') + 1); }

    private boolean isAlreadyExistsError(IOException e) {
        String message = e.getMessage();
        return message != null && (message.contains("already exists") || message.contains("422") || message.contains("name already exists on this branch"));
    }

    private boolean fileExists(GHRepository repository, String branch, String filePath) {
        try { repository.getFileContent(filePath, branch); return true; }
        catch (IOException e) { return false; }
    }

    @FunctionalInterface
    private interface RetryableOperation { PushResult execute() throws IOException; }

    // === REST helpers ===
    public List<Map<String, Object>> getRepositoryContents(String repoUrl, String token, String path) {
        String apiUrl = repoUrl.replace("https://github.com", GITHUB_API_URL + "/repos") + "/contents/" + (path != null ? path : "");
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
        ResponseEntity<Map> response = restTemplate.exchange(GITHUB_API_URL + "/user", HttpMethod.GET, entity, Map.class);
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
    private final String commitHtmlUrl;  
    private final String previousSha;     

    public PushResult(String commitHash, String filePath, PushAction action, String message) {
        this(commitHash, filePath, action, message, null, null);
    }

    public PushResult(String commitHash, String filePath, PushAction action, String message,
                      String commitHtmlUrl, String previousSha) {
        this.commitHash = commitHash;
        this.filePath = filePath;
        this.action = action;
        this.message = message;
        this.commitHtmlUrl = commitHtmlUrl;
        this.previousSha = previousSha;
    }

    public String getCommitHash() { return commitHash; }
    public String getFilePath() { return filePath; }
    public PushAction getAction() { return action; }
    public String getMessage() { return message; }
    public String getCommitHtmlUrl() { return commitHtmlUrl; }
    public String getPreviousSha() { return previousSha; }
}

    

    public enum PushAction { CREATED, UPDATED, SKIPPED }
    public enum FileHandlingStrategy { UPDATE_IF_EXISTS, CREATE_NEW_ALWAYS, FAIL_IF_EXISTS }
   
public Optional<String> tryGetFileContent(String repoUrl, String token, String filePath) {
    try {
        return Optional.ofNullable(getFileContent(repoUrl, token, filePath));
    } catch (RuntimeException e) {
        return Optional.empty();
    }
}


public List<String> findComposeCandidatesAtRoot(String repoUrl, String token) {
    List<String> out = new ArrayList<>();
    try {
        List<Map<String, Object>> root = getRepositoryContents(repoUrl, token, null);
        if (root == null) return out;
        for (Map<String, Object> f : root) {
            String name = String.valueOf(f.get("name"));
            if ("docker-compose.yml".equals(name) || "docker-compose.yaml".equals(name) || "compose.yaml".equals(name)) {
                out.add(name);
            }
        }
    } catch (Exception ignored) {}
    return out;
}


public Optional<String> getLatestCommitShaForPath(String token, String repoFullName, String branch, String path) {
    try {
        GitHub gh = GitHub.connectUsingOAuth(token);
        GHRepository repo = gh.getRepository(repoFullName);
        
        PagedIterable<GHCommit> commits = repo.queryCommits().from(branch).path(path).list();
        Iterator<GHCommit> it = commits.iterator();
        if (it.hasNext()) {
            return Optional.ofNullable(it.next().getSHA1());
        }
    } catch (Exception ignored) {}
    return Optional.empty();
}


public String getDefaultBranch(String token, String repoFullName) throws IOException {
    GitHub gh = GitHub.connectUsingOAuth(token);
    GHRepository repo = gh.getRepository(repoFullName);
    return repo.getDefaultBranch();
}

public List<String> getAllRepositoryFiles(String repoUrl, String token, String branch) {  
    try {  
        
        String[] parts = repoUrl.replace("https://github.com/", "").split("/");  
        String owner = parts[0];  
        String repo = parts[1];  
          
       
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/git/trees/%s?recursive=1",   
                                     owner, repo, branch);  
          
        HttpHeaders headers = createHeaders(token);  
        HttpEntity<String> entity = new HttpEntity<>(headers);  
          
        ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, Map.class);  
          
        List<String> files = new ArrayList<>();  
        if (response.getBody() != null && response.getBody().containsKey("tree")) {  
            List<Map<String, Object>> tree = (List<Map<String, Object>>) response.getBody().get("tree");  
              
            for (Map<String, Object> item : tree) {  
                if ("blob".equals(item.get("type"))) { // Seulement les fichiers, pas les dossiers  
                    files.add((String) item.get("path"));  
                }  
            }  
        }  
          
        return files;  
    } catch (RestClientException e) {  
        throw new RuntimeException("Error fetching all repository files: " + e.getMessage());  
    }  
}

}