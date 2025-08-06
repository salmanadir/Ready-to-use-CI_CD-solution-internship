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
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Authentication required"
                ));
            }

            if (request == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Request is null"
                ));
            }
            
            if (request.getRepoId() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Repo ID is null in request"
                ));
            }

            System.out.println("Repo ID: " + request.getRepoId());

            Optional<Repo> repoOpt = repoRepository.findById(request.getRepoId());
            if (!repoOpt.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Repository not found with ID: " + request.getRepoId()
                ));
            }

            Repo repo = repoOpt.get();
            System.out.println("Repository trouvé:");
            System.out.println("  - Full Name: " + repo.getFullName());
            System.out.println("  - URL: " + repo.getUrl());
            System.out.println("  - Default Branch: " + repo.getDefaultBranch());

            // Vérifier que l'utilisateur authentifié est bien le propriétaire du repo
            User authenticatedUser = (User) authentication.getPrincipal();
            if (!repo.getUser().getId().equals(authenticatedUser.getId())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "You don't have permission to modify this repository"
                ));
            }

            if (repo.getUser().getToken() == null || repo.getUser().getToken().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "GitHub token not found for user"
                ));
            }

            System.out.println("Token présent: " + (repo.getUser().getToken() != null && !repo.getUser().getToken().isEmpty()));

            StackAnalysis info = request.getTechStackInfo();
            if (info == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "TechStackInfo is null in request"
                ));
            }

            System.out.println("StackAnalysis:");
            System.out.println("  - Build Tool: " + info.getBuildTool());
            System.out.println("  - Java Version: " + info.getJavaVersion());
            System.out.println("  - Working Directory: " + info.getWorkingDirectory());
            System.out.println("  - Stack Type: " + info.getStackType());

            String templatePath = templateService.getTemplatePath(info);
            System.out.println("Template Path: " + templatePath);

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("javaVersion", info.getJavaVersion());
            placeholders.put("workingDirectory", info.getWorkingDirectory());

            String content = templateRenderer.renderTemplate(templatePath, placeholders);
            System.out.println("Content généré (longueur): " + content.length());
            
            // Correction du nom de fichier pour éviter "maven-1.yml"
            String buildTool = info.getBuildTool() != null ? info.getBuildTool().toLowerCase() : "ci";
            String fileName = buildTool + "-ci.yml";
            String filePath = ".github/workflows/" + fileName;
            
            System.out.println("File Path: " + filePath);
            System.out.println("File Handling Strategy: " + request.getFileHandlingStrategy());
            
            String token = repo.getUser().getToken();

            // 🔧 Test de connectivité GitHub amélioré
            System.out.println("Test de connectivité avec GitHub...");
            try {
                Map<String, Object> userInfo = gitHubService.getUserInfo(token);
                System.out.println("Connexion GitHub OK - User: " + userInfo.get("login"));
                
                // 🔧 Test spécifique de connectivité au repository
                boolean connectionTest = gitHubService.testGitHubConnection(token, repo.getFullName());
                if (!connectionTest) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "GitHub repository connection test failed. Please check repository name and permissions."
                    ));
                }
                
            } catch (Exception e) {
                System.err.println("Erreur de connexion GitHub: " + e.getMessage());
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "GitHub connection failed: " + e.getMessage() + 
                              ". Vérifiez que le token a les permissions 'repo' et que le repository existe."
                ));
            }
            
            System.out.println(">>> Final file path envoyé à GitHubService : " + filePath);

            System.out.println("Appel à pushWorkflowToGitHub...");
            GitHubService.PushResult result = gitHubService.pushWorkflowToGitHub(
                token, 
                repo.getFullName(), 
                repo.getDefaultBranch(), 
                filePath, 
                content,
                GitHubService.FileHandlingStrategy.valueOf(request.getFileHandlingStrategy().name())
            );

            System.out.println("Résultat GitHub:");
            System.out.println("  - Commit Hash: " + result.getCommitHash());
            System.out.println("  - Message: " + result.getMessage());
            System.out.println("  - Action: " + result.getAction());

            // Sauvegarde en base (si commit est effectué)
            if (result.getCommitHash() != null) {
                CiWorkflow workflow = workflowService.saveWorkflowAfterPush(repo, content, result.getCommitHash());
                System.out.println("Workflow sauvegardé en base avec ID: " + workflow.getCiWorkflowId());
            }

            System.out.println("=== FIN GÉNÉRATION WORKFLOW - SUCCÈS ===");

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", result.getMessage(),
                "commitHash", result.getCommitHash(),
                "filePath", result.getFilePath()
            ));

        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
            e.printStackTrace();
            
            // 🔧 Messages d'erreur plus spécifiques
            String userMessage = e.getMessage();
            if (userMessage != null && userMessage.contains("Not Found")) {
                userMessage = "Repository ou branche introuvable. Vérifiez que le repository existe et que vous avez les permissions nécessaires.";
            } else if (userMessage != null && userMessage.contains("Bad credentials")) {
                userMessage = "Token GitHub invalide. Veuillez vous reconnecter à GitHub.";
            } else if (userMessage != null && userMessage.contains("403")) {
                userMessage = "Permissions insuffisantes. Assurez-vous que votre token GitHub a les permissions 'repo'.";
            }
            
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "IO Error: " + userMessage
            ));
        } catch (IllegalArgumentException e) {
            System.err.println("Validation Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Validation Error: " + e.getMessage()
            ));
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Unexpected error: " + e.getMessage()
            ));
        }
    }

    // 🔧 Nouveau endpoint pour tester la connectivité GitHub
    @PostMapping("/test-github-connection")
    public ResponseEntity<?> testGitHubConnection(@RequestBody Map<String, Long> request, 
                                                Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Authentication required"
                ));
            }

            Long repoId = request.get("repoId");
            if (repoId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Repo ID is required"
                ));
            }

            Optional<Repo> repoOpt = repoRepository.findById(repoId);
            if (!repoOpt.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Repository not found"
                ));
            }

            Repo repo = repoOpt.get();
            User authenticatedUser = (User) authentication.getPrincipal();
            
            if (!repo.getUser().getId().equals(authenticatedUser.getId())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Access denied"
                ));
            }

            String token = repo.getUser().getToken();
            boolean success = gitHubService.testGitHubConnection(token, repo.getFullName());

            return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "GitHub connection successful" : "GitHub connection failed",
                "repository", repo.getFullName()
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Error testing connection: " + e.getMessage()
            ));
        }
    }
}