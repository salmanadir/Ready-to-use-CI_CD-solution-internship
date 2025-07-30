package com.example.demo.controller;

import com.example.demo.model.CiWorkflow;
import com.example.demo.model.Repo;
import com.example.demo.model.TechStackInfo;
import com.example.demo.repository.RepoRepository;
import com.example.demo.service.*;
import com.example.demo.ci.template.TemplateRenderer;
import com.example.demo.dto.WorkflowGenerationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/workflows")
public class CiWorkflowController {

    @Autowired
    private WorkflowTemplateService templateService;

    @Autowired
    private TemplateRenderer templateRenderer;

    @Autowired
    private GitHubService gitHubService;

    @Autowired
    private CiWorkflowService workflowService;

    @Autowired
    private RepoRepository repoRepository;

    @PostMapping("/generate")
@Transactional
public ResponseEntity<String> generateAndPushWorkflow(@RequestBody WorkflowGenerationRequest request) {
    try {
        
        if (request == null) {
            return ResponseEntity.badRequest().body("Request is null");
        }
        
        if (request.getRepoId() == null) {
            return ResponseEntity.badRequest().body("Repo ID is null in request");
        }

        Optional<Repo> repoOpt = repoRepository.findById(request.getRepoId());
        if (!repoOpt.isPresent()) {
            return ResponseEntity.badRequest().body("Repository not found with ID: " + request.getRepoId());
        }

        Repo repo = repoOpt.get();

        if (repo.getUser() == null) {
            return ResponseEntity.badRequest().body("User not found for repository ID: " + request.getRepoId());
        }

        if (repo.getUser().getToken() == null || repo.getUser().getToken().isEmpty()) {
            return ResponseEntity.badRequest().body("GitHub token not found for user");
        }

        TechStackInfo info = request.getTechStackInfo();
        if (info == null) {
            return ResponseEntity.badRequest().body("TechStackInfo is null in request");
        }

        // Génération du contenu du workflow
        String templatePath = templateService.getTemplatePath(info);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("javaVersion", info.getJavaVersion());
        placeholders.put("workingDirectory", info.getWorkingDirectory());

        String content = templateRenderer.renderTemplate(templatePath, placeholders);
        
        // Déterminer le nom du fichier
        String filePath = ".github/workflows/" + info.getBuildTool().toLowerCase() + ".yml";
      
        
        String token = repo.getUser().getToken();

        // Push vers GitHub avec la stratégie choisie
        GitHubService.PushResult result = gitHubService.pushWorkflowToGitHub(
            token, 
            repo.getFullName(), 
            repo.getDefaultBranch(), 
            filePath, 
            content,
            GitHubService.FileHandlingStrategy.valueOf(request.getFileHandlingStrategy().name())
        );

        // Sauvegarde en base (ghir si commit est effectué)
        if (result.getCommitHash() != null) {
            CiWorkflow workflow = workflowService.saveWorkflowAfterPush(repo, content, result.getCommitHash());
        }

        return ResponseEntity.ok(result.getMessage() + " (Commit: " + result.getCommitHash() + ")");

    } catch (IOException e) {
        return ResponseEntity.internalServerError().body("IO Error: " + e.getMessage());
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body("Validation Error: " + e.getMessage());
    } catch (Exception e) {
        return ResponseEntity.internalServerError().body("Unexpected error: " + e.getMessage());
    }
}
}