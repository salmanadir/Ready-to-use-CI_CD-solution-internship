package com.example.demo.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.CdWorkflow;
import com.example.demo.model.CiWorkflow;
import com.example.demo.model.Repo;
import com.example.demo.model.User;
import com.example.demo.repository.RepoRepository;
import com.example.demo.repository.CiWorkflowRepository;
import com.example.demo.repository.CdWorkflowRepository;

@RestController
@RequestMapping("/api/history")
@CrossOrigin(origins = "*") 
public class HistoryController {

    @Autowired
    private RepoRepository repoRepository;
    
    @Autowired
    private CiWorkflowRepository ciWorkflowRepository;
    
    @Autowired
    private CdWorkflowRepository cdWorkflowRepository;

    @GetMapping("/user-activity")
    public ResponseEntity<?> getUserHistory(Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            Long userId = user.getId(); // ✅ Fixed: use getId() instead of getUserId()
            
            List<Map<String, Object>> historyItems = new ArrayList<>();
            
            // Get all repositories for this user
            List<Repo> userRepos = repoRepository.findByUser_UserId(userId);
            
            for (Repo repo : userRepos) {
                // Add repository connection event
                Map<String, Object> repoEvent = new HashMap<>();
                repoEvent.put("id", "repo-" + repo.getRepoId());
                repoEvent.put("repoName", getRepoName(repo));
                repoEvent.put("action", "Repository Connected");
                repoEvent.put("type", "repo");
                repoEvent.put("status", "success");
                repoEvent.put("createdAt", LocalDateTime.now().minusDays(1)); // Mock for now
                historyItems.add(repoEvent);
            }
            
            // Get all CD workflows for this user's repos
            List<CdWorkflow> cdWorkflows = cdWorkflowRepository.findByCiWorkflow_Repo_User_Id(userId);  
            for (CdWorkflow cd : cdWorkflows) {
                Map<String, Object> cdEvent = new HashMap<>();
                cdEvent.put("id", "cd-" + cd.getCdWorkflowId()); // ✅ Fixed: use getCdWorkflowId()
                cdEvent.put("repoName", getRepoNameFromCd(cd));
                cdEvent.put("action", "CD Workflow Generated");
                cdEvent.put("type", "cd");
                cdEvent.put("status", getCdStatus(cd));
                cdEvent.put("createdAt", cd.getCreatedAt()); // ✅ Use real timestamp
                cdEvent.put("workflowContent", getCdWorkflowContent(cd));
                cdEvent.put("workflowName", "CD Workflow");
                historyItems.add(cdEvent);
            }
            
            // Get CI workflows by iterating through repos
            for (Repo repo : userRepos) {
                // Check if repo has CI workflows - safe navigation
                if (repo.getCiWorkflows() != null && !repo.getCiWorkflows().isEmpty()) {
                    for (CiWorkflow ci : repo.getCiWorkflows()) {
                        Map<String, Object> ciEvent = new HashMap<>();
                        ciEvent.put("id", "ci-" + ci.getCiWorkflowId()); // ✅ Fixed: use getCiWorkflowId()
                        ciEvent.put("repoName", getRepoName(repo));
                        ciEvent.put("action", "CI Workflow Generated");
                        ciEvent.put("type", "ci");
                        ciEvent.put("status", getCiStatus(ci));
                        ciEvent.put("createdAt", ci.getCreatedAt()); // ✅ Use real timestamp
                        ciEvent.put("workflowContent", getCiWorkflowContent(ci));
                        ciEvent.put("workflowName", "CI Workflow");
                        historyItems.add(ciEvent);
                    }
                }
            }
            
            // Sort by creation date (most recent first)
            historyItems.sort((a, b) -> {
                LocalDateTime dateA = (LocalDateTime) a.get("createdAt");
                LocalDateTime dateB = (LocalDateTime) b.get("createdAt");
                
                if (dateA == null && dateB == null) return 0;
                if (dateA == null) return 1;
                if (dateB == null) return -1;
                
                return dateB.compareTo(dateA);
            });
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "history", historyItems,
                "totalItems", historyItems.size()
            ));
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error fetching history: " + e.getMessage()
            ));
        }
    }
    
    // Helper methods
    private String getRepoName(Repo repo) {
        if (repo == null) return "Unknown Repo";
        
        if (repo.getGithubRepoId() != null) return repo.getGithubRepoId();
        if (repo.getFullName() != null) return repo.getFullName();
        return "Repository #" + repo.getRepoId();
    }
    
    private String getRepoNameFromCd(CdWorkflow cd) {
        if (cd == null || cd.getCiWorkflow() == null || cd.getCiWorkflow().getRepo() == null) {
            return "Unknown Repo";
        }
        return getRepoName(cd.getCiWorkflow().getRepo());
    }
    
    private String getCiStatus(CiWorkflow ci) {
        try {
            if (ci.getStatus() != null) {
                return ci.getStatus().toString().toLowerCase();
            }
        } catch (Exception e) {}
        return "success";
    }
    
    private String getCdStatus(CdWorkflow cd) {
        try {
            if (cd.getStatus() != null) {
                return cd.getStatus().toString().toLowerCase();
            }
        } catch (Exception e) {}
        return "success";
    }
    
    private String getCiWorkflowContent(CiWorkflow ci) {
        // ✅ Try to get real content first
        try {
            if (ci.getContent() != null && !ci.getContent().isEmpty()) {
                return ci.getContent();
            }
        } catch (Exception e) {
            // Method might not exist, fallback to mock
        }
        
        // Fallback to mock content
        return """
name: CI
on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '18'
    - name: Install dependencies
      run: npm install
    - name: Run tests
      run: npm test
        """;
    }
    
    private String getCdWorkflowContent(CdWorkflow cd) {
        // ✅ Try to get real content first
        try {
            if (cd.getContent() != null && !cd.getContent().isEmpty()) {
                return cd.getContent();
            }
        } catch (Exception e) {
            // Keep fallback
        }
        
        // Fallback to mock content
        return """
name: CD
on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '18'
    - name: Install dependencies
      run: npm install
    - name: Build
      run: npm run build
    - name: Deploy
      run: npm run deploy
        """;
    }
}