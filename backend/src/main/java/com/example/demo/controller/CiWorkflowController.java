package com.example.demo.controller;

import com.example.demo.model.CiWorkflow;
import com.example.demo.model.Repo;
import com.example.demo.model.User;
import com.example.demo.repository.RepoRepository;
import com.example.demo.service.*;
import com.example.demo.ci.template.TemplateRenderer;
import com.example.demo.dto.StackAnalysis;
import com.example.demo.dto.WorkflowGenerationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/workflows")
@CrossOrigin(origins = "*") 
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
public ResponseEntity<?> generateAndPushWorkflow(@RequestBody WorkflowGenerationRequest request,
                                                 Authentication authentication) {
    try {
        System.out.println("=== DÉBUT GÉNÉRATION WORKFLOW ===");

        // 1. Authentification
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Authentication required"));
        }

        // 2. Validation request
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Request is null"));
        }
        if (request.getRepoId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Repo ID is null in request"));
        }

        // 3. Récupération repo
        Optional<Repo> repoOpt = repoRepository.findById(request.getRepoId());
        if (!repoOpt.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "message", "Repository not found with ID: " + request.getRepoId()));
        }
        Repo repo = repoOpt.get();
        System.out.println("Repository trouvé: " + repo.getFullName() + " | Branche: " + repo.getDefaultBranch());

        // 4. Vérification propriétaire
        User authenticatedUser = (User) authentication.getPrincipal();
        if (!repo.getUser().getId().equals(authenticatedUser.getId())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                    "You don't have permission to modify this repository"));
        }

        // 5. Vérification token
        String token = repo.getUser().getToken();
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub token not found for user"));
        }

        // 6. Stack info
        StackAnalysis info = request.getTechStackInfo();
        if (info == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null in request"));
        }
        System.out.println("StackAnalysis: BuildTool=" + info.getBuildTool() + ", Java=" + info.getJavaVersion());

        // 7. Génération du contenu
        String templatePath = templateService.getTemplatePath(info);
        String content = templateRenderer.renderTemplate(templatePath, Map.of(
                "javaVersion", info.getJavaVersion(),
                "workingDirectory", info.getWorkingDirectory()
        ));
        System.out.println("Template path: " + templatePath + " | Taille contenu: " + content.length());

        // 8. Construction du nom exact du fichier
        String buildTool = info.getBuildTool() != null ? info.getBuildTool().toLowerCase() : "ci";
        String fileName = buildTool + "-ci.yml";
        String filePath = ".github/workflows/" + fileName;
        System.out.println("Nom final du fichier: " + filePath);

        // 9. Test connexion GitHub
        try {
            Map<String, Object> userInfo = gitHubService.getUserInfo(token);
            System.out.println("Connexion GitHub OK - User: " + userInfo.get("login"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub connection failed: " + e.getMessage()));
        }

        // 10. Push workflow
        GitHubService.PushResult result = gitHubService.pushWorkflowToGitHub(
                token,
                repo.getFullName(),
                repo.getDefaultBranch(),
                filePath,
                content,
                GitHubService.FileHandlingStrategy.valueOf(request.getFileHandlingStrategy().name())
        );

        // 11. Sauvegarde si succès
        if (result.getCommitHash() != null) {
            CiWorkflow workflow = workflowService.saveWorkflowAfterPush(repo, content, result.getCommitHash());
            System.out.println("Workflow sauvegardé en base avec ID: " + workflow.getCiWorkflowId());
        }

        System.out.println("=== FIN GÉNÉRATION WORKFLOW ===");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", result.getMessage(),
                "commitHash", result.getCommitHash(),
                "filePath", result.getFilePath()
        ));

    } catch (IOException e) {
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "IO Error: " + e.getMessage()));
    } catch (Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Unexpected error: " + e.getMessage()));
    }


    }
}