package com.example.demo.controller;

import com.example.demo.ci.template.TemplateRenderer;
import com.example.demo.dto.ComposePlan;
import com.example.demo.dto.ContainerPlan;
import com.example.demo.dto.StackAnalysis;
import com.example.demo.dto.WorkflowGenerationRequest;
import com.example.demo.model.CiWorkflow;
import com.example.demo.model.Repo;
import com.example.demo.model.User;
import com.example.demo.repository.RepoRepository;
import com.example.demo.service.CiWorkflowService;
import com.example.demo.service.ContainerizationService;
import com.example.demo.service.ContainerizationWriter;
import com.example.demo.service.GitHubService;
import com.example.demo.service.WorkflowTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

    @Autowired private WorkflowTemplateService templateService;
    @Autowired private TemplateRenderer templateRenderer;
    @Autowired private GitHubService gitHubService;
    @Autowired private CiWorkflowService workflowService;
    @Autowired private RepoRepository repoRepository;
    @Autowired private ContainerizationService containerizationService;
    @Autowired private ContainerizationWriter containerizationWriter;

    // =====================================================================================
    // A) PREVIEW (ne push rien) — renvoie le plan + contenu généré du Dockerfile/Compose
    // =====================================================================================
    @PostMapping("/docker/preview")
    public ResponseEntity<?> previewDocker(@RequestBody WorkflowGenerationRequest request,
                                           Authentication authentication) {
        try {
            // Auth & repo
            var repo = requireAuthAndRepo(authentication, request.getRepoId());
            if (repo == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false, "message", "Authentication required or repo not found / not owned"
            ));

            String token = repo.getUser().getToken();
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub token not found for user"));
            }
            gitHubService.setCurrentToken(token);

            StackAnalysis info = request.getTechStackInfo();
            if (info == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null"));

            // Options docker (si null -> valeurs par défaut)
            WorkflowGenerationRequest.DockerOptions docker = request.getDocker();
            if (docker == null) docker = new WorkflowGenerationRequest.DockerOptions();

            // Plan container + compose (en lecture seule)
            ContainerPlan containerPlan = containerizationService.plan(
                    repo.getUrl(),
                    token,
                    (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                            ? repo.getFullName() : docker.getImageNameOverride(),
                    info,
                    docker.getImageNameOverride(),
                    docker.getRegistry()
            );

            ComposePlan composePlan = null;
            if (docker.isGenerateCompose()) {
                composePlan = containerizationService.planComposeForDev(info);
            }

            // prêt pour CI ? (on exige que le Dockerfile existe déjà si plan dit "à générer")
            boolean readyForCi = !containerPlan.isShouldGenerateDockerfile()
                    && (!docker.isGenerateCompose() || (containerPlan.isHasCompose() || (composePlan != null && composePlan.shouldGenerateCompose)));

            Map<String, Object> out = new HashMap<>();
            out.put("success", true);
            out.put("readyForCi", readyForCi);
            out.put("containerPlan", containerPlan);
            if (composePlan != null) out.put("composePlan", composePlan);

            return ResponseEntity.ok(out);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =====================================================================================
    // B) APPLY DOCKERFILE (push) — à appeler après preview si shouldGenerateDockerfile=true
    // =====================================================================================
    @PostMapping("/dockerfile/apply")
    @Transactional
    public ResponseEntity<?> applyDockerfile(@RequestBody WorkflowGenerationRequest request,
                                             Authentication authentication) {
        try {
            var repo = requireAuthAndRepo(authentication, request.getRepoId());
            if (repo == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false, "message", "Authentication required or repo not found / not owned"
            ));

            String token = repo.getUser().getToken();
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub token not found for user"));
            }
            gitHubService.setCurrentToken(token);

            StackAnalysis info = request.getTechStackInfo();
            if (info == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null"));

            WorkflowGenerationRequest.DockerOptions docker = request.getDocker();
            if (docker == null) docker = new WorkflowGenerationRequest.DockerOptions();

            ContainerPlan containerPlan = containerizationService.plan(
                    repo.getUrl(),
                    token,
                    (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                            ? repo.getFullName() : docker.getImageNameOverride(),
                    info,
                    docker.getImageNameOverride(),
                    docker.getRegistry()
            );

            if (!containerPlan.isShouldGenerateDockerfile()) {
                // Rien à générer (déjà présent)
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Dockerfile already present — nothing to apply",
                        "dockerfilePath", containerPlan.getDockerfilePath()
                ));
            }

            containerizationWriter.ensureDockerfile(
                    token,
                    repo.getFullName(),
                    repo.getDefaultBranch(),
                    containerPlan,
                    docker.getDockerfileStrategy() != null
                            ? docker.getDockerfileStrategy()
                            : GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Dockerfile generated & pushed",
                    "dockerfilePath", containerPlan.getDockerfilePath()
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "IO Error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =====================================================================================
    // C) APPLY COMPOSE DEV (push) — si on a demandé generateCompose lors du preview
    // =====================================================================================
    @PostMapping("/compose/apply")
    @Transactional
    public ResponseEntity<?> applyCompose(@RequestBody WorkflowGenerationRequest request,
                                          Authentication authentication) {
        try {
            var repo = requireAuthAndRepo(authentication, request.getRepoId());
            if (repo == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false, "message", "Authentication required or repo not found / not owned"
            ));

            String token = repo.getUser().getToken();
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub token not found for user"));
            }
            gitHubService.setCurrentToken(token);

            StackAnalysis info = request.getTechStackInfo();
            if (info == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null"));

            WorkflowGenerationRequest.DockerOptions docker = request.getDocker();
            if (docker == null || !docker.isGenerateCompose()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Docker compose generation not requested"
                ));
            }

            ComposePlan composePlan = containerizationService.planComposeForDev(info);
            if (composePlan == null || !composePlan.shouldGenerateCompose) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "No compose file needed or Spring+DB not detected"
                ));
            }

            containerizationWriter.ensureCompose(
                    token,
                    repo.getFullName(),
                    repo.getDefaultBranch(),
                    composePlan,
                    docker.getComposeStrategy() != null
                            ? docker.getComposeStrategy()
                            : GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "docker-compose.dev.yml generated & pushed",
                    "composePath", composePlan.composePath
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "IO Error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =====================================================================================
    // D) GENERATE CI (push) — NE LE FAIT QUE SI Dockerfile (et compose si demandé) SONT PRÊTS
    // =====================================================================================
    @PostMapping("/generate")
    @Transactional
    public ResponseEntity<?> generateAndPushWorkflow(@RequestBody WorkflowGenerationRequest request,
                                                     Authentication authentication) {
        try {
            System.out.println("=== DÉBUT GÉNÉRATION WORKFLOW ===");

            var repo = requireAuthAndRepo(authentication, request.getRepoId());
            if (repo == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false, "message", "Authentication required or repo not found / not owned"
            ));

            String token = repo.getUser().getToken();
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub token not found for user"));
            }
            gitHubService.setCurrentToken(token);

            StackAnalysis info = request.getTechStackInfo();
            if (info == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null in request"));
            }

            WorkflowGenerationRequest.DockerOptions docker = request.getDocker();
            if (docker == null) docker = new WorkflowGenerationRequest.DockerOptions();

            // 1) Recalcule le plan pour vérifier les prérequis
            ContainerPlan containerPlan = containerizationService.plan(
                    repo.getUrl(),
                    token,
                    (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                            ? repo.getFullName() : docker.getImageNameOverride(),
                    info,
                    docker.getImageNameOverride(),
                    docker.getRegistry()
            );

            // 1.a) Si Dockerfile manque encore → stop (on exige preview/apply avant CI)
            if (containerPlan.isShouldGenerateDockerfile()) {
                return ResponseEntity.status(428).body(Map.of(
                        "success", false,
                        "message", "Dockerfile not applied yet. Preview + apply the Dockerfile before generating CI.",
                        "hintPreview", "/api/workflows/docker/preview",
                        "hintApply", "/api/workflows/dockerfile/apply"
                ));
            }

            // 1.b) Si compose demandé et pas présent → stop (même logique)
            if (docker.isGenerateCompose()) {
                ComposePlan cp = containerizationService.planComposeForDev(info);
                if (cp != null && cp.shouldGenerateCompose && !containerPlan.isHasCompose()) {
                    return ResponseEntity.status(428).body(Map.of(
                        "success", false,
                        "message", "docker-compose.dev.yml not applied yet. Preview + apply compose before generating CI.",
                        "hintPreview", "/api/workflows/docker/preview",
                        "hintApply", "/api/workflows/compose/apply"
                    ));
                }
            }

            // 2) Choix template
            String templatePath = templateService.getTemplatePath(info);

            // 3) Placeholders
            Map<String, String> replacements = new HashMap<>();
            String workingDir = (info.getWorkingDirectory() == null || info.getWorkingDirectory().isBlank()) ? "." : info.getWorkingDirectory();
            replacements.put("workingDirectory", workingDir);

            String buildToolLower = (info.getBuildTool() == null) ? "" : info.getBuildTool().toLowerCase();
            if ("maven".equals(buildToolLower) || "gradle".equals(buildToolLower)) {
                replacements.put("javaVersion", (info.getJavaVersion() != null && !info.getJavaVersion().isBlank())
                        ? info.getJavaVersion() : "17");
            } else if ("npm".equals(buildToolLower)) {
                String nodeVersion = null;
                if (info.getProjectDetails() != null) {
                    Object v = info.getProjectDetails().get("nodeVersion");
                    nodeVersion = (v != null) ? v.toString() : null;
                }
                replacements.put("nodeVersion", resolveNodeVersionForActions(nodeVersion));
            }

            // 3.b) Placeholders Docker
            replacements.putAll(containerPlan.placeholders());

            String content = templateRenderer.renderTemplate(templatePath, replacements);

            // 4) Nom du fichier workflow
            String fileName = switch (buildToolLower) {
                case "maven" -> "maven-ci.yml";
                case "gradle" -> "gradle-ci.yml";
                case "npm" -> "npm-ci.yml";
                default -> "ci.yml";
            };
            String filePath = ".github/workflows/" + fileName;

            // 5) Test GitHub quick
            try { gitHubService.getUserInfo(token); } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub connection failed: " + e.getMessage()));
            }

            // 6) Push du workflow
            GitHubService.PushResult result = gitHubService.pushWorkflowToGitHub(
                    token,
                    repo.getFullName(),
                    repo.getDefaultBranch(),
                    filePath,
                    content,
                    GitHubService.FileHandlingStrategy.valueOf(request.getFileHandlingStrategy().name())
            );

            // 7) Save DB
            if (result.getCommitHash() != null) {
                CiWorkflow workflow = workflowService.saveWorkflowAfterPush(repo, content, result.getCommitHash());
                System.out.println("Workflow sauvegardé en base avec ID: " + workflow.getCiWorkflowId());
            }

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

    // ======================
    // Helpers (privés)
    // ======================
    private Repo requireAuthAndRepo(Authentication authentication, Long repoId) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) return null;
            if (repoId == null) return null;

            Optional<Repo> repoOpt = repoRepository.findById(repoId);
            if (repoOpt.isEmpty()) return null;

            Repo repo = repoOpt.get();
            User user = (User) authentication.getPrincipal();
            if (!repo.getUser().getId().equals(user.getId())) return null;

            return repo;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveNodeVersionForActions(String raw) {
        if (raw == null || raw.isBlank()) return "lts/*";
        String v = raw.trim();

        if (v.equalsIgnoreCase("latest") || v.equalsIgnoreCase("current")) return "lts/*";
        if (v.matches("^[vV]?\\d+(?:\\.\\d+){0,2}$")) {
            return v.replaceFirst("^[vV]", "");
        }
        java.util.regex.Matcher mx = java.util.regex.Pattern.compile("^(\\d+)\\.x$").matcher(v);
        if (mx.find()) return mx.group(1);

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(?:\\^|~|>=|<=|>|<)?\\s*(\\d+)").matcher(v.replace("v",""));
        if (m.find()) return m.group(1);

        return "lts/*";
    }
}
