package com.example.demo.controller;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import com.example.demo.model.User;

import java.util.Map;
import java.util.HashMap;
import java.util.Date;

@RestController
@RequestMapping("/test/api/user")
public class UserController {

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(Authentication authentication) {
        // Get authenticated user from security context
        User user = (User) authentication.getPrincipal();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail() != null ? user.getEmail() : "",
            "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
            "githubId", user.getGithubId(),
            "token", user.getToken() != null ? user.getToken() : ""
        ));
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(Authentication authentication, @RequestBody Map<String, Object> updates) {
        User user = (User) authentication.getPrincipal();
        
        // Mock update - in real implementation, you'd update the database
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Profile updated successfully");
        response.put("updated_fields", updates.keySet());
        response.put("user_id", user.getId());
        response.put("username", user.getUsername());
        
        return ResponseEntity.ok(response);
    }
    @PostMapping("/create-folder-and-file")
public ResponseEntity<?> createFolderAndFile(Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    String accessToken = user.getToken();
    
    try {
        RestTemplate restTemplate = new RestTemplate();
        
        // Your actual repository
        String owner = user.getUsername();
        String repo = "Dining-_Philosophers_Problem";
        String folderPath = "cicd-platform-test";
        String fileName = "test-file.txt";
        String fullPath = folderPath + "/" + fileName;
        
        // Create file content
        String content = "Hello from CI/CD Platform!\n\n" +
                        "This file was created automatically by the CI/CD platform.\n" +
                        "Repository: " + repo + "\n" +
                        "Created by: " + user.getUsername() + "\n" +
                        "Created at: " + new Date() + "\n\n" +
                        "This demonstrates that our OAuth token has write access to repositories!";
        
        // GitHub API URL for creating file
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + fullPath;
        
        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Encode content to base64 (required by GitHub API)
        String encodedContent = java.util.Base64.getEncoder().encodeToString(content.getBytes());
        
        // Request body
        Map<String, Object> requestBody = Map.of(
            "message", "Create test folder and file via CI/CD Platform",
            "content", encodedContent
        );
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        
        // Make the API call
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            url,
            HttpMethod.PUT,
            entity,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Folder and file created successfully!",
            "repository", owner + "/" + repo,
            "file_path", fullPath,
            "file_url", "https://github.com/" + owner + "/" + repo + "/blob/main/" + fullPath,
            "github_response", response.getBody()
        ));
        
    } catch (Exception e) {
        return ResponseEntity.status(500)
            .body(Map.of(
                "error", "Failed to create folder and file: " + e.getMessage(),
                "repository", user.getUsername() + "/30-days-aws-projects"
            ));
    }
}
@PostMapping("/update-file")
public ResponseEntity<?> updateFile(Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    String accessToken = user.getToken();
    
    try {
        RestTemplate restTemplate = new RestTemplate();
        
        // Same repository and file path
        String owner = user.getUsername();
        String repo = "30-days-aws-projects";
        String folderPath = "cicd-platform-test";
        String fileName = "test-file.txt";
        String fullPath = folderPath + "/" + fileName;
        
        String baseUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + fullPath;
        
        // Step 1: Get the current file to obtain its SHA
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.set("Authorization", "Bearer " + accessToken);
        getHeaders.set("Accept", "application/vnd.github.v3+json");
        
        HttpEntity<String> getEntity = new HttpEntity<>(getHeaders);
        
        ResponseEntity<Map<String, Object>> getResponse = restTemplate.exchange(
            baseUrl,
            HttpMethod.GET,
            getEntity,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        
        // Extract SHA from the response with null check
        Map<String, Object> getResponseBody = getResponse.getBody();
        if (getResponseBody == null || !getResponseBody.containsKey("sha")) {
            return ResponseEntity.status(500)
                .body(Map.of(
                    "error", "Failed to retrieve file SHA from GitHub response.",
                    "repository", owner + "/" + repo,
                    "tip", "Make sure the file exists first by running /create-folder-and-file"
                ));
        }
        String currentSha = (String) getResponseBody.get("sha");
        
        // Step 2: Create updated content
        String updatedContent = "Hello from CI/CD Platform - UPDATED!\n\n" +
                              "This file has been UPDATED by the CI/CD platform.\n" +
                              "Repository: " + repo + "\n" +
                              "Originally created by: " + user.getUsername() + "\n" +
                              "Last updated at: " + new Date() + "\n" +
                              "Update count: " + Math.random() + "\n\n" +
                              "This demonstrates that our OAuth token can UPDATE existing files!\n\n" +
                              "Previous SHA: " + currentSha;
        
        // Step 3: Update the file
        HttpHeaders updateHeaders = new HttpHeaders();
        updateHeaders.set("Authorization", "Bearer " + accessToken);
        updateHeaders.set("Accept", "application/vnd.github.v3+json");
        updateHeaders.setContentType(MediaType.APPLICATION_JSON);
        
        // Encode updated content to base64
        String encodedContent = java.util.Base64.getEncoder().encodeToString(updatedContent.getBytes());
        
        // Request body with SHA (required for updates)
        Map<String, Object> updateRequestBody = Map.of(
            "message", "Update test file via CI/CD Platform - " + new Date(),
            "content", encodedContent,
            "sha", currentSha  // ← This is required for updates!
        );
        
        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateRequestBody, updateHeaders);
        
        // Make the update API call
        ResponseEntity<Map<String, Object>> updateResponse = restTemplate.exchange(
            baseUrl,
            HttpMethod.PUT,
            updateEntity,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        
        Map<String, Object> updateResponseBody = updateResponse.getBody();
        Object newSha = null;
        if (updateResponseBody != null && updateResponseBody.get("content") instanceof Map) {
            newSha = ((Map<?, ?>) updateResponseBody.get("content")).get("sha");
        }
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "File updated successfully!",
            "repository", owner + "/" + repo,
            "file_path", fullPath,
            "file_url", "https://github.com/" + owner + "/" + repo + "/blob/main/" + fullPath,
            "previous_sha", currentSha,
            "new_sha", newSha,
            "github_response", updateResponseBody
        ));
        
    } catch (Exception e) {
        return ResponseEntity.status(500)
            .body(Map.of(
                "error", "Failed to update file: " + e.getMessage(),
                "repository", user.getUsername() + "/30-days-aws-projects",
                "tip", "Make sure the file exists first by running /create-folder-and-file"
            ));
    }
}
}