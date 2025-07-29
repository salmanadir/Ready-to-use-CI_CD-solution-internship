package com.example.demo.controller;

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
           
            System.out.println("Request received: " + request);
            System.out.println("Repo ID from request: " + request.getRepoId());

            // Vérification de la requête
            if (request == null) {
                return ResponseEntity.badRequest().body("Request is null");
            }
            
            if (request.getRepoId() == null) {
                return ResponseEntity.badRequest().body("Repo ID is null in request");
            }

            // Récupération du repository du database avec vérifications
            Optional<Repo> repoOpt = repoRepository.findById(request.getRepoId());
            System.out.println("Repo found in database: " + repoOpt.isPresent());

            if (!repoOpt.isPresent()) {
                System.out.println("ERROR: Repository not found for ID: " + request.getRepoId());
                return ResponseEntity.badRequest().body("Repository not found with ID: " + request.getRepoId());
            }

            Repo repo = repoOpt.get();
            System.out.println("Repo retrieved - ID: " + repo.getRepoId());
            System.out.println("Repo full name: " + repo.getFullName());
            System.out.println("User is null? " + (repo.getUser() == null));

            // Vérification de l'utilisateur
            if (repo.getUser() == null) {
                System.out.println("ERROR: User is null for repository ID: " + request.getRepoId());
                return ResponseEntity.badRequest().body("User not found for repository ID: " + request.getRepoId());
            }

            System.out.println("User ID: " + repo.getUser().getUserId());
            System.out.println("User username: " + repo.getUser().getUsername());
            System.out.println("Token exists: " + (repo.getUser().getToken() != null && !repo.getUser().getToken().isEmpty()));

            // Vérification du token
            if (repo.getUser().getToken() == null || repo.getUser().getToken().isEmpty()) {
                System.out.println("ERROR: Token is null or empty for user: " + repo.getUser().getUserId());
                return ResponseEntity.badRequest().body("GitHub token not found for user");
            }

            // Vérification des informations techniques
            TechStackInfo info = request.getTechStackInfo();
            if (info == null) {
                return ResponseEntity.badRequest().body("TechStackInfo is null in request");
            }

            System.out.println("TechStack - Java Version: " + info.getJavaVersion());
            System.out.println("TechStack - Build Tool: " + info.getBuildTool());
            System.out.println("TechStack - Working Directory: " + info.getWorkingDirectory());

            // Génération du template
            String templatePath = templateService.getTemplatePath(info);
            System.out.println("Template path: " + templatePath);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("javaVersion", info.getJavaVersion());
            placeholders.put("workingDirectory", info.getWorkingDirectory());

            String content = templateRenderer.renderTemplate(templatePath, placeholders);
            System.out.println("Template rendered successfully. Content length: " + content.length());

            // Préparation du fichier
            String filePath = ".github/workflows/" + info.getBuildTool().toLowerCase() + ".yml";
            System.out.println("File path: " + filePath);

            // Push vers GitHub
            String token = repo.getUser().getToken();
            System.out.println("Pushing to GitHub...");
            gitHubService.pushWorkflowToGitHub(token, repo.getFullName(), repo.getDefaultBranch(), filePath, content);
            System.out.println("Successfully pushed to GitHub");

            // Sauvegarde en base
            System.out.println("Saving to database...");
            workflowService.saveWorkflowToDatabase(repo, content, filePath);
            System.out.println("Successfully saved to database");

            System.out.println("=== DEBUGGING END - SUCCESS ===");
            return ResponseEntity.ok("Workflow pushed and saved successfully.");

        } catch (IOException e) {
            System.err.println("IOException occurred: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("IO Error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.err.println("IllegalArgumentException occurred: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Validation Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Unexpected error: " + e.getMessage());
        }
    }
}