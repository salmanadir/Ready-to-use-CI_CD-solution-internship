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
@CrossOrigin(origins = "http://localhost:5173", allowedHeaders = "*", allowCredentials = "true")
public class HistoryController {

    @Autowired
    private RepoRepository repoRepository;
    
    @Autowired
    private CiWorkflowRepository ciWorkflowRepository;
    
    @Autowired
    private CdWorkflowRepository cdWorkflowRepository;

    @GetMapping("/user-activity")
    @CrossOrigin(origins = "http://localhost:5173", allowedHeaders = "*", allowCredentials = "true")
    public ResponseEntity<?> getUserHistory(Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            Long userId = user.getId();
            
            List<Map<String, Object>> historyItems = new ArrayList<>();
            
            // ✅ Use existing method that works
            List<Repo> userRepos = repoRepository.findByUser_UserId(userId);
            
            // ✅ Group operations by repository
            for (Repo repo : userRepos) {
                Map<String, Object> repoEvent = createRepoHistoryItem(repo, userId);
                historyItems.add(repoEvent);
            }
            
            // ✅ Fix the sorting with proper type casting
            historyItems.sort((a, b) -> {
                Object objA = a.get("lastActivity");
                Object objB = b.get("lastActivity");
                
                LocalDateTime dateA = null;
                LocalDateTime dateB = null;
                
                if (objA instanceof LocalDateTime) {
                    dateA = (LocalDateTime) objA;
                }
                if (objB instanceof LocalDateTime) {
                    dateB = (LocalDateTime) objB;
                }
                
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
    
    // ✅ Fixed method using only existing repository methods
    private Map<String, Object> createRepoHistoryItem(Repo repo, Long userId) {
        Map<String, Object> repoEvent = new HashMap<>();
        
        String repoName = getCleanRepoName(repo);
        
        repoEvent.put("id", "repo-" + repo.getRepoId());
        repoEvent.put("repoName", repoName);
        repoEvent.put("type", "repository");
        repoEvent.put("status", "active");
        
        List<Map<String, Object>> operations = new ArrayList<>();
        
        // Handle repository creation timestamp
        LocalDateTime repoCreatedAt = repo.getCreatedAt() != null ? 
            repo.getCreatedAt() : LocalDateTime.now().minusDays(1);
        LocalDateTime lastActivity = repoCreatedAt;
        
        // ✅ ALWAYS add repository connection
        Map<String, Object> connectionOp = new HashMap<>();
        connectionOp.put("type", "connection");
        connectionOp.put("action", "Repository Connected");
        connectionOp.put("timestamp", repoCreatedAt);
        connectionOp.put("status", "success");
        operations.add(connectionOp);
        
        // ✅ Add CI workflows if they exist
        if (repo.getCiWorkflows() != null && !repo.getCiWorkflows().isEmpty()) {
            for (CiWorkflow ci : repo.getCiWorkflows()) {
                if (ci.getContent() != null && !ci.getContent().trim().isEmpty()) {
                    LocalDateTime ciTimestamp = ci.getCreatedAt() != null ? 
                        ci.getCreatedAt() : repoCreatedAt.plusMinutes(30);
                    
                    Map<String, Object> ciOp = new HashMap<>();
                    ciOp.put("type", "ci");
                    ciOp.put("action", "CI Workflow Generated");
                    ciOp.put("timestamp", ciTimestamp);
                    ciOp.put("status", getCiStatus(ci));
                    ciOp.put("workflowContent", ci.getContent());
                    operations.add(ciOp);
                    
                    if (ciTimestamp.isAfter(lastActivity)) {
                        lastActivity = ciTimestamp;
                    }
                }
            }
        }
        
        // ✅ Use existing method - find CD workflows by user ID and filter manually
        List<CdWorkflow> allCdWorkflows = cdWorkflowRepository.findByCiWorkflow_Repo_User_Id(userId);
        for (CdWorkflow cd : allCdWorkflows) {
            // Check if this CD belongs to current repo
            if (cd.getCiWorkflow() != null && 
                cd.getCiWorkflow().getRepo() != null && 
                cd.getCiWorkflow().getRepo().getRepoId().equals(repo.getRepoId()) &&
                cd.getContent() != null && !cd.getContent().trim().isEmpty()) {
                
                LocalDateTime cdTimestamp = cd.getCreatedAt() != null ? 
                    cd.getCreatedAt() : repoCreatedAt.plusHours(1);
                
                Map<String, Object> cdOp = new HashMap<>();
                cdOp.put("type", "cd");
                cdOp.put("action", "CD Workflow Generated");
                cdOp.put("timestamp", cdTimestamp);
                cdOp.put("status", getCdStatus(cd));
                cdOp.put("workflowContent", cd.getContent());
                operations.add(cdOp);
                
                if (cdTimestamp.isAfter(lastActivity)) {
                    lastActivity = cdTimestamp;
                }
            }
        }
        
        // ✅ Fix sorting with proper type handling
        operations.sort((a, b) -> {
            Object objA = a.get("timestamp");
            Object objB = b.get("timestamp");
            
            LocalDateTime dateA = null;
            LocalDateTime dateB = null;
            
            if (objA instanceof LocalDateTime) {
                dateA = (LocalDateTime) objA;
            }
            if (objB instanceof LocalDateTime) {
                dateB = (LocalDateTime) objB;
            }
            
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            
            return dateB.compareTo(dateA);
        });
        
        repoEvent.put("operations", operations);
        repoEvent.put("lastActivity", lastActivity);
        repoEvent.put("operationCount", operations.size());
        
        String summaryAction = createSummaryAction(operations);
        repoEvent.put("action", summaryAction);
        repoEvent.put("createdAt", lastActivity);
        
        return repoEvent;
    }
    
    private String createSummaryAction(List<Map<String, Object>> operations) {
        boolean hasConnection = false;
        boolean hasCI = false;
        boolean hasCD = false;
        
        for (Map<String, Object> op : operations) {
            String type = (String) op.get("type");
            if ("connection".equals(type)) {
                hasConnection = true;
            } else if ("ci".equals(type)) {
                hasCI = true;
            } else if ("cd".equals(type)) {
                hasCD = true;
            }
        }
        
        if (hasConnection && hasCI && hasCD) {
            return "Full CI/CD Pipeline Setup";
        } else if (hasConnection && hasCI) {
            return "Repository with CI Setup";
        } else if (hasConnection) {
            return "Repository Connected";
        } else {
            return "Repository Activity";
        }
    }
    
    private String getCleanRepoName(Repo repo) {
        if (repo.getFullName() != null && !repo.getFullName().isEmpty()) {
            return repo.getFullName();
        }
        
        if (repo.getGithubRepoId() != null && !repo.getGithubRepoId().isEmpty()) {
            try {
                Long.parseLong(repo.getGithubRepoId());
                return "Repository #" + repo.getGithubRepoId();
            } catch (NumberFormatException e) {
                return repo.getGithubRepoId();
            }
        }
        
        return "Repository #" + repo.getRepoId();
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
}