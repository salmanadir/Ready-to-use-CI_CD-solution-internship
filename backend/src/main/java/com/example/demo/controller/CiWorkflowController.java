package com.example.demo.controller;

import com.example.demo.ci.template.TemplateRenderer;
import com.example.demo.service.ContainerizationService;
import com.example.demo.service.ContainerizationWriter;
import com.example.demo.dto.ContainerPlan;
import com.example.demo.dto.ComposePlan;
import com.example.demo.dto.StackAnalysis;
import com.example.demo.dto.WorkflowGenerationRequest;
import com.example.demo.model.CiWorkflow;
import com.example.demo.model.Repo;
import com.example.demo.model.User;
import com.example.demo.repository.RepoRepository;
import com.example.demo.service.CiWorkflowService;
import com.example.demo.service.GitHubService;
import com.example.demo.service.WorkflowTemplateService;
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

    @Autowired
    private ContainerizationService containerizationService;

    @Autowired
    private ContainerizationWriter containerizationWriter;

    @PostMapping("/generate")
    @Transactional
    public ResponseEntity<?> generateAndPushWorkflow(@RequestBody WorkflowGenerationRequest request,
                                                     Authentication authentication) {
        try {
            System.out.println("=== DÉBUT GÉNÉRATION WORKFLOW ===");

            // 1) Auth
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Authentication required"));
            }

            // 2) Validation request
            if (request == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Request is null"));
            }
            if (request.getRepoId() == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Repo ID is null in request"));
            }

            // 3) Repo
            Optional<Repo> repoOpt = repoRepository.findById(request.getRepoId());
            if (repoOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "Repository not found with ID: " + request.getRepoId()));
            }
            Repo repo = repoOpt.get();
            System.out.println("Repository trouvé: " + repo.getFullName() + " | Branche: " + repo.getDefaultBranch());

            // 4) Propriétaire
            User authenticatedUser = (User) authentication.getPrincipal();
            if (!repo.getUser().getId().equals(authenticatedUser.getId())) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                        "You don't have permission to modify this repository"));
            }

            // 5) Token
            String token = repo.getUser().getToken();
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub token not found for user"));
            }
            gitHubService.setCurrentToken(token);

            // 6) Stack info
            StackAnalysis info = request.getTechStackInfo();
            if (info == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null in request"));
            }
            System.out.println("StackAnalysis: BuildTool=" + info.getBuildTool() + ", Java=" + info.getJavaVersion());

            // 6.1) Options Docker -> FORCÉ à ON (build image impératif)
            WorkflowGenerationRequest.DockerOptions docker = request.getDocker();
            if (docker == null) docker = new WorkflowGenerationRequest.DockerOptions();
            docker.setEnable(true);

            // 6.2) Plan container (détection Dockerfile/compose + décision)
            ContainerPlan containerPlan = containerizationService.plan(
                    repo.getUrl(),
                    token,
                    (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                            ? repo.getFullName() : docker.getImageNameOverride(),
                    info,
                    docker.getImageNameOverride(),
                    docker.getRegistry()
            );

            // 6.3) Si pas de Dockerfile -> on le crée AVANT le workflow
            if (containerPlan.isShouldGenerateDockerfile()) {
                containerizationWriter.ensureDockerfile(
                        token,
                        repo.getFullName(),
                        repo.getDefaultBranch(),
                        containerPlan,
                        docker.getDockerfileStrategy() != null
                                ? docker.getDockerfileStrategy()
                                : GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS
                );
                System.out.println("Dockerfile créé/poussé: " + containerPlan.getDockerfilePath());
            }

            // 6.4) (Optionnel) compose de dev si demandé
            if (docker.isGenerateCompose()) {
                ComposePlan composePlan = containerizationService.planComposeForDev(info);
                if (composePlan != null && composePlan.shouldGenerateCompose) {
                    containerizationWriter.ensureCompose(
                            token,
                            repo.getFullName(),
                            repo.getDefaultBranch(),
                            composePlan,
                            docker.getComposeStrategy() != null
                                    ? docker.getComposeStrategy()
                                    : GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS
                    );
                    System.out.println("docker-compose.dev.yml créé/poussé");
                }
            }

            // 7) Choix du template CI (toujours "avec Docker")
            // OPTION A: si ta WorkflowTemplateService a une seule méthode et renvoie déjà le template docker:
            String templatePath = templateService.getTemplatePath(info);

            // OPTION B: si tu as encore la version "avec boolean", dé-commente:
            // String templatePath = templateService.getTemplatePath(info, true);

            System.out.println("Template choisi: " + templatePath);

            // 7.1) Placeholders généraux
            Map<String, String> replacements = new HashMap<>();
            String workingDir = (info.getWorkingDirectory() == null || info.getWorkingDirectory().isBlank())
                    ? "."
                    : info.getWorkingDirectory();
            replacements.put("workingDirectory", workingDir);

            String buildToolLower = (info.getBuildTool() == null) ? "" : info.getBuildTool().toLowerCase();

            if ("maven".equals(buildToolLower) || "gradle".equals(buildToolLower)) {
                replacements.put("javaVersion", (info.getJavaVersion() != null && !info.getJavaVersion().isBlank())
                        ? info.getJavaVersion()
                        : "17");
            } else if ("npm".equals(buildToolLower)) {
                String nodeVersion = null;
                if (info.getProjectDetails() != null) {
                    Object v = info.getProjectDetails().get("nodeVersion");
                    nodeVersion = (v != null) ? v.toString() : null;
                }
                replacements.put("nodeVersion", resolveNodeVersionForActions(nodeVersion));
            }

            // 7.2) Placeholders Docker (toujours présents) — utilise l’aide ContainerPlan.placeholders()
            replacements.putAll(containerPlan.placeholders());

            String content = templateRenderer.renderTemplate(templatePath, replacements);
            System.out.println("Template path: " + templatePath + " | Taille contenu: " + content.length());

            // 8) Nom du fichier workflow
            String fileName = switch (buildToolLower) {
                case "maven" -> "maven-ci.yml";
                case "gradle" -> "gradle-ci.yml";
                case "npm" -> "npm-ci.yml";
                default -> "ci.yml";
            };
            String filePath = ".github/workflows/" + fileName;
            System.out.println("Nom final du fichier: " + filePath);

            // 9) Test connexion GitHub
            try {
                Map<String, Object> userInfo = gitHubService.getUserInfo(token);
                System.out.println("Connexion GitHub OK - User: " + userInfo.get("login"));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub connection failed: " + e.getMessage()));
            }

            // 10) Push du workflow CI
            GitHubService.PushResult result = gitHubService.pushWorkflowToGitHub(
                    token,
                    repo.getFullName(),
                    repo.getDefaultBranch(),
                    filePath,
                    content,
                    GitHubService.FileHandlingStrategy.valueOf(request.getFileHandlingStrategy().name())
            );

            // 11) Sauvegarde DB
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
