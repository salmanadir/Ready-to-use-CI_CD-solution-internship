package com.example.demo.controller;  
  
import java.util.List;  
import java.util.Map;  
  
import org.springframework.beans.factory.annotation.Autowired;  
import org.springframework.http.ResponseEntity;  
import org.springframework.security.core.Authentication;  
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
  
    // ✅ SUPPRIMÉ : L'ancien endpoint d'authentification  
    // @PostMapping("/authenticate") - Remplacé par le système OAuth JWT  
  
    @GetMapping("/available")  // ✅ Plus besoin de {userId}  
    public ResponseEntity<?> getAvailableRepos(Authentication authentication) {  
        try {  
            User user = (User) authentication.getPrincipal();  
            List<Map<String, Object>> repos = repoSelectionService.getUserGithubRepos(user.getId());  
            return ResponseEntity.ok(Map.of("success", true, "repositories", repos));  
        } catch (RuntimeException e) {  
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));  
        }  
    }  
  
    @PostMapping("/select")  
    public ResponseEntity<?> selectRepository(Authentication authentication, @RequestBody Map<String, Object> request) {  
        try {  
            User user = (User) authentication.getPrincipal();  
            Map<String, Object> repoData = (Map<String, Object>) request.get("repoData");  
  
            Repo selectedRepo = repoSelectionService.selectRepository(user.getId(), repoData);  
            return ResponseEntity.ok(Map.of("success", true, "repository", selectedRepo));  
        } catch (RuntimeException e) {  
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));  
        }  
    }  
  
    @GetMapping("/selected")  // ✅ Plus besoin de user/{userId}  
    public ResponseEntity<?> getSelectedRepos(Authentication authentication) {  
        try {  
            User user = (User) authentication.getPrincipal();  
            List<Repo> repos = repoSelectionService.getUserSelectedRepos(user.getId());  
            return ResponseEntity.ok(Map.of("success", true, "repositories", repos));  
        } catch (RuntimeException e) {  
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));  
        }  
    }  
  
    @DeleteMapping("/repository/{repoId}")  // ✅ Plus besoin de user/{userId}  
    public ResponseEntity<?> deselectRepository(Authentication authentication, @PathVariable Long repoId) {  
        try {  
            User user = (User) authentication.getPrincipal();  
            repoSelectionService.deselectRepository(user.getId(), repoId);  
            return ResponseEntity.ok(Map.of("success", true, "message", "Repository supprimé"));  
        } catch (RuntimeException e) {  
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));  
        }  
    }  
}