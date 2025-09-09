package com.example.demo.controller;

import com.example.demo.model.Repo;
import com.example.demo.repository.RepoRepository;
import com.example.demo.service.CdWorkflowGenerationService;
import com.example.demo.repository.CiWorkflowRepository;
import com.example.demo.service.GitHubService;
import com.example.demo.service.DeploymentUrlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/cd-workflow")
@CrossOrigin(origins = "*")
public class CdWorkflowController {
    @Autowired private CdWorkflowGenerationService cdWorkflowGenerationService;
    @Autowired private GitHubService gitHubService;
    @Autowired private RepoRepository repoRepository;
    @Autowired private DeploymentUrlService deploymentUrlService;
    @Autowired private CiWorkflowRepository ciWorkflowRepository;
    // GET LIVE URLS: Returns the live URLs for deployed services
    @GetMapping("/live-urls")
    public ResponseEntity<?> getLiveUrls(@RequestParam Long repoId, @RequestParam String vmHost, Authentication authentication) {
        try {
            Repo repo = requireAuthAndRepo(authentication, repoId);
            if (repo == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false, "message", "Authentication required or repo not found / not owned"
            ));
            var urls = deploymentUrlService.getLiveUrls(repo, vmHost);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "urls", urls
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // PREVIEW: Generate the workflow YAML but do not push
    @PostMapping("/preview")
    public ResponseEntity<?> previewCdWorkflow(@RequestParam Long repoId, Authentication authentication) {
        try {
            Repo repo = requireAuthAndRepo(authentication, repoId);
            if (repo == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false, "message", "Authentication required or repo not found / not owned"
            ));
            
            // Check if CI workflow exists for this repo
            if (ciWorkflowRepository.findByRepo(repo).isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "message", "You must generate a CI workflow for this repository before generating a CD workflow."
                ));
            }

            // Check if Docker Compose exists
            String token = repo.getUser().getToken();
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub token not found for user"));
            }
            gitHubService.setCurrentToken(token);

            // Check if Docker Compose exists
            // Try to find common Docker Compose file names
            boolean hasCompose = false;
            try {
                // Check for common compose file names
                String[] composeFiles = {"docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml"};
                for (String fileName : composeFiles) {
                    try {
                        gitHubService.getFileContent(repo.getUrl(), token, fileName);
                        hasCompose = true;
                        break;
                    } catch (Exception ignore) {
                        // File doesn't exist, try next
                    }
                }
            } catch (Exception e) {
                // Error checking files
            }

            if (!hasCompose) {
                return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(Map.of(
                    "success", false,
                    "missingCompose", true,
                    "message", "Docker Compose file is required for CD workflow generation. Would you like to generate one?",
                    "hintPreviewCompose", "/api/workflows/compose/prod/preview",
                    "hintApplyCompose", "/api/workflows/compose/prod/apply"
                ));
            }

            String workflowYaml = cdWorkflowGenerationService.generateCdWorkflow("${{ secrets.VM_HOST }}", "${{ secrets.VM_USER }}");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "workflowYaml", workflowYaml
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // APPLY: Generate and push the workflow YAML to the user's repo
    @PostMapping("/apply")
    public ResponseEntity<?> applyCdWorkflow(@RequestParam Long repoId, Authentication authentication) {
        try {
            Repo repo = requireAuthAndRepo(authentication, repoId);
            if (repo == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false, "message", "Authentication required or repo not found / not owned"
            ));
            
            // Check if CI workflow exists for this repo
            if (ciWorkflowRepository.findByRepo(repo).isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "message", "You must generate a CI workflow for this repository before generating a CD workflow."
                ));
            }

            String token = repo.getUser().getToken();
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub token not found for user"));
            }
            gitHubService.setCurrentToken(token);

            // Check if Docker Compose exists
            boolean hasCompose = false;
            try {
                // Check for common compose file names
                String[] composeFiles = {"docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml"};
                for (String fileName : composeFiles) {
                    try {
                        gitHubService.getFileContent(repo.getUrl(), token, fileName);
                        hasCompose = true;
                        break;
                    } catch (Exception ignore) {
                        // File doesn't exist, try next
                    }
                }
            } catch (Exception e) {
                // Error checking files
            }

            if (!hasCompose) {
                return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(Map.of(
                    "success", false,
                    "missingCompose", true,
                    "message", "Docker Compose file is required for CD workflow generation. Please generate one first.",
                    "hintPreviewCompose", "/api/workflows/compose/prod/preview",
                    "hintApplyCompose", "/api/workflows/compose/prod/apply"
                ));
            }

            String workflowYaml = cdWorkflowGenerationService.generateCdWorkflow("${{ secrets.VM_HOST }}", "${{ secrets.VM_USER }}");
            GitHubService.PushResult pr = gitHubService.pushWorkflowToGitHub(
                token,
                repo.getFullName(),
                repo.getDefaultBranch(),
                ".github/workflows/cd-deploy.yml",
                workflowYaml,
                GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "CD workflow generated & pushed",
                    "commitHash", pr != null ? pr.getCommitHash() : null
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "IO Error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Helper: Auth + repo lookup 
    private Repo requireAuthAndRepo(Authentication authentication, Long repoId) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) return null;
            if (repoId == null) return null;

            java.util.Optional<Repo> repoOpt = repoRepository.findById(repoId);
            if (repoOpt.isEmpty()) return null;

            Repo repo = repoOpt.get();
            Object principal = authentication.getPrincipal();
            if (!(principal instanceof com.example.demo.model.User)) return null;
            com.example.demo.model.User user = (com.example.demo.model.User) principal;
            if (repo.getUser() == null || !repo.getUser().getId().equals(user.getId())) return null;

            return repo;
        } catch (Exception e) {
            return null;
        }
    }
}
