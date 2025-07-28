package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.Repo;
import com.example.demo.model.User;
import com.example.demo.service.RepoSelectionService;

@RestController
@RequestMapping("/api/repositories")
@CrossOrigin(origins = "*")
public class RepoController {

    @Autowired
    private RepoSelectionService repoSelectionService;

    @PostMapping("/authenticate")
    public ResponseEntity<Map<String, Object>> authenticateWithToken(@RequestBody Map<String, String> request) {
        try {  
            String token = request.get("token");
            if (token == null || token.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Token requis"));
            }

            User user = repoSelectionService.createOrUpdateUser(token);
            return ResponseEntity.ok(Map.of("success", true, "message", "Authentification réussie", "user", user));
        } catch (RuntimeException e) {  
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}/available")  
    public ResponseEntity<?> getAvailableRepos(@PathVariable Long userId) {  
        try {  
            List<Map<String, Object>> repos = repoSelectionService.getUserGithubRepos(userId);  
            return ResponseEntity.ok(Map.of("success", true, "repositories", repos));  
        } catch (RuntimeException e) {  
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));  
        }  
    }  
  
    @PostMapping("/select")  
    public ResponseEntity<?> selectRepository(@RequestBody Map<String, Object> request) {  
        try {  

            if (request.get("userId") == null || request.get("repoData") == null) {  
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Paramètres manquants"));  
            }  
  
            Long userId = Long.valueOf(request.get("userId").toString());  
            Map<String, Object> repoData = (Map<String, Object>) request.get("repoData");  
  
            Repo selectedRepo = repoSelectionService.selectRepository(userId, repoData);  
            return ResponseEntity.ok(Map.of("success", true, "repository", selectedRepo));  
        } catch (NumberFormatException e) {  
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "userId invalide"));  
        } catch (RuntimeException e) {  
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));  
        }  
    }  
  
    @GetMapping("/user/{userId}/selected")  
    public ResponseEntity<?> getSelectedRepos(@PathVariable Long userId) {  
        try {  
            List<Repo> repos = repoSelectionService.getUserSelectedRepos(userId);  
            return ResponseEntity.ok(Map.of("success", true, "repositories", repos));  
        } catch (RuntimeException e) {  
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));  
        }  
    }  
  
    @DeleteMapping("/user/{userId}/repository/{repoId}")  
    public ResponseEntity<?> deselectRepository(@PathVariable Long userId, @PathVariable Long repoId) {  
        try {  
            repoSelectionService.deselectRepository(userId, repoId);  
            return ResponseEntity.ok(Map.of("success", true, "message", "Repository supprimé"));  
        } catch (RuntimeException e) {  
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));  
        }  
    }  
}