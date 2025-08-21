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
import com.example.demo.service.*;
import com.example.demo.service.ContainerizationService.ServiceContainerPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

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
    @Autowired private DockerfileHistoryService dockerfileHistoryService; // <-- NEW

    // =====================================================================================
    // A) PREVIEW (ne push rien) — SINGLE ou MULTI selon request.services
    // =====================================================================================
    @PostMapping("/docker/preview")
    public ResponseEntity<?> previewDocker(@RequestBody WorkflowGenerationRequest request,
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

            WorkflowGenerationRequest.DockerOptions docker = request.getDocker();
            if (docker == null) docker = new WorkflowGenerationRequest.DockerOptions();

            if (request.getServices()!=null && !request.getServices().isEmpty()) {
                var plans = containerizationService.planMulti(
                        repo.getUrl(), token, repo.getFullName(), request.getServices(), docker.getRegistry());
                boolean readyForCi = plans.stream().noneMatch(ServiceContainerPlan::isShouldGenerateDockerfile);
                return ResponseEntity.ok(Map.of("success", true, "mode", "multi", "readyForCi", readyForCi, "plans", plans));
            }

            StackAnalysis info = request.getTechStackInfo();
            if (info == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null"));

            ContainerPlan containerPlan = containerizationService.plan(
                    repo.getUrl(), token,
                    (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                            ? repo.getFullName() : docker.getImageNameOverride(),
                    info, docker.getImageNameOverride(), docker.getRegistry()
            );

            ComposePlan composePlan = null;
            if (docker.isGenerateCompose()) composePlan = containerizationService.planComposeForDev(info);

            boolean readyForCi = !containerPlan.isShouldGenerateDockerfile()
                    && (!docker.isGenerateCompose() || (containerPlan.isHasCompose() || (composePlan != null && composePlan.shouldGenerateCompose)));

            Map<String,Object> out = new HashMap<>();
            out.put("success", true);
            out.put("mode", "single");
            out.put("readyForCi", readyForCi);
            out.put("containerPlan", containerPlan);
            if (composePlan != null) out.put("composePlan", composePlan);
            return ResponseEntity.ok(out);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =====================================================================================
    // B) APPLY DOCKERFILE (push) — SINGLE ou MULTI
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

            WorkflowGenerationRequest.DockerOptions docker = request.getDocker();
            if (docker == null) docker = new WorkflowGenerationRequest.DockerOptions();

            // ---- MULTI
            if (request.getServices()!=null && !request.getServices().isEmpty()) {
                var plans = containerizationService.planMulti(
                        repo.getUrl(), token, repo.getFullName(), request.getServices(), docker.getRegistry());

                List<String> pushedPaths = new ArrayList<>();
                Map<String,String> commitsByPath = new HashMap<>();

                for (ServiceContainerPlan p : plans) {
                    if (!p.isShouldGenerateDockerfile()) continue;

                    GitHubService.PushResult pr = gitHubService.pushWorkflowToGitHub(
                            token, repo.getFullName(), repo.getDefaultBranch(),
                            p.getDockerfilePath(), p.getGeneratedDockerfileContent(),
                            docker.getDockerfileStrategy() != null ? docker.getDockerfileStrategy()
                                                                  : GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS
                    );

                    // Historiser
                    dockerfileHistoryService.recordMulti(repo, p, pr);

                    pushedPaths.add(p.getDockerfilePath());
                    if (pr != null) commitsByPath.put(p.getDockerfilePath(), pr.getCommitHash());
                }

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "mode", "multi",
                        "message", pushedPaths.isEmpty() ? "All Dockerfiles already present — nothing to apply"
                                                         : "Dockerfiles generated & pushed",
                        "dockerfilePaths", pushedPaths,
                        "commits", commitsByPath
                ));
            }

            // ---- SINGLE
            StackAnalysis info = request.getTechStackInfo();
            if (info == null) return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null"));

            ContainerPlan containerPlan = containerizationService.plan(
                    repo.getUrl(), token,
                    (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                            ? repo.getFullName() : docker.getImageNameOverride(),
                    info, docker.getImageNameOverride(), docker.getRegistry()
            );

            if (!containerPlan.isShouldGenerateDockerfile()) {
                return ResponseEntity.ok(Map.of(
                        "success", true, "mode", "single",
                        "message", "Dockerfile already present — nothing to apply",
                        "dockerfilePath", containerPlan.getDockerfilePath()
                ));
            }

            GitHubService.PushResult pr = containerizationWriter.ensureDockerfile(
                    token, repo.getFullName(), repo.getDefaultBranch(),
                    containerPlan,
                    docker.getDockerfileStrategy() != null ? docker.getDockerfileStrategy()
                                                           : GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS
            );

            // Historiser
            dockerfileHistoryService.recordSingle(repo, containerPlan, pr);

            return ResponseEntity.ok(Map.of(
                    "success", true, "mode", "single",
                    "message", "Dockerfile generated & pushed",
                    "dockerfilePath", containerPlan.getDockerfilePath(),
                    "commitHash", pr != null ? pr.getCommitHash() : null
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "IO Error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =====================================================================================
    // C) APPLY COMPOSE DEV (push) — single (inchangé)
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
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Docker compose generation not requested"));
            }

            var composePlan = containerizationService.planComposeForDev(info);
            if (composePlan == null || !composePlan.shouldGenerateCompose) {
                return ResponseEntity.ok(Map.of("success", true, "message", "No compose file needed or Spring+DB not detected"));
            }

            containerizationWriter.ensureCompose(
                    token, repo.getFullName(), repo.getDefaultBranch(),
                    composePlan,
                    docker.getComposeStrategy() != null ? docker.getComposeStrategy()
                                                        : GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS
            );

            return ResponseEntity.ok(Map.of("success", true, "message", "docker-compose.dev.yml generated & pushed",
                    "composePath", composePlan.composePath));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "IO Error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

   // =====================================================================================
// D) GENERATE CI (push) — SINGLE ou MULTI
// =====================================================================================
@PostMapping("/generate")
@Transactional
public ResponseEntity<?> generateAndPushWorkflow(@RequestBody WorkflowGenerationRequest request,
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

        WorkflowGenerationRequest.DockerOptions docker = request.getDocker();
        if (docker == null) docker = new WorkflowGenerationRequest.DockerOptions();

        // =============================================
        // MODE MULTI (plusieurs services)
        // =============================================
        if (request.getServices() != null && !request.getServices().isEmpty()) {
            List<Map<String, Object>> results = new ArrayList<>();

            int index = 0;
            for (Map<String,Object> svc : request.getServices()) {
                StackAnalysis info = StackAnalysis.fromMap(svc); // helper à coder dans StackAnalysis
                if (info == null) continue;

                // plan containerisation pour vérifier dockerfile
                ContainerPlan containerPlan = containerizationService.plan(
                        repo.getUrl(), token,
                        (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                                ? repo.getFullName() : docker.getImageNameOverride(),
                        info, docker.getImageNameOverride(), docker.getRegistry()
                );

                if (containerPlan.isShouldGenerateDockerfile()) {
                    return ResponseEntity.status(428).body(Map.of(
                            "success", false,
                            "message", "Dockerfile not applied yet for service: " + info.getWorkingDirectory(),
                            "service", info.getWorkingDirectory()
                    ));
                }

                String templatePath = templateService.getTemplatePath(info);

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

                replacements.putAll(containerPlan.placeholders());

                String content = templateRenderer.renderTemplate(templatePath, replacements);

                // nom unique par service
                String fileName = "ci-" + (info.getWorkingDirectory() != null ? info.getWorkingDirectory().replace("/", "-") : "service-" + index) + ".yml";
                String filePath = ".github/workflows/" + fileName;

                try { gitHubService.getUserInfo(token); } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub connection failed: " + e.getMessage()));
                }

                GitHubService.PushResult pr = gitHubService.pushWorkflowToGitHub(
                        token, repo.getFullName(), repo.getDefaultBranch(), filePath, content,
                        GitHubService.FileHandlingStrategy.valueOf(request.getFileHandlingStrategy().name())
                );

                if (pr.getCommitHash() != null) {
                    CiWorkflow workflow = workflowService.saveWorkflowAfterPush(repo, content, pr.getCommitHash());
                    System.out.println("Workflow MULTI sauvegardé en base avec ID: " + workflow.getCiWorkflowId());
                }

                results.add(Map.of(
                        "service", info.getWorkingDirectory(),
                        "filePath", filePath,
                        "commitHash", pr.getCommitHash()
                ));
                index++;
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mode", "multi",
                    "workflows", results
            ));
        }

        // =============================================
        // MODE SINGLE (inchangé)
        // =============================================
        StackAnalysis info = request.getTechStackInfo();
        if (info == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null in request"));
        }

        ContainerPlan containerPlan = containerizationService.plan(
                repo.getUrl(), token,
                (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                        ? repo.getFullName() : docker.getImageNameOverride(),
                info, docker.getImageNameOverride(), docker.getRegistry()
        );

        if (containerPlan.isShouldGenerateDockerfile()) {
            return ResponseEntity.status(428).body(Map.of(
                    "success", false,
                    "message", "Dockerfile not applied yet. Preview + apply the Dockerfile before generating CI.",
                    "hintPreview", "/api/workflows/docker/preview",
                    "hintApply", "/api/workflows/dockerfile/apply"
            ));
        }

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

        String templatePath = templateService.getTemplatePath(info);

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

        replacements.putAll(containerPlan.placeholders());

        String content = templateRenderer.renderTemplate(templatePath, replacements);

        String fileName = switch (buildToolLower) {
            case "maven" -> "maven-ci.yml";
            case "gradle" -> "gradle-ci.yml";
            case "npm" -> "npm-ci.yml";
            default -> "ci.yml";
        };
        String filePath = ".github/workflows/" + fileName;

        try { gitHubService.getUserInfo(token); } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub connection failed: " + e.getMessage()));
        }

        GitHubService.PushResult result = gitHubService.pushWorkflowToGitHub(
                token, repo.getFullName(), repo.getDefaultBranch(), filePath, content,
                GitHubService.FileHandlingStrategy.valueOf(request.getFileHandlingStrategy().name())
        );

        if (result.getCommitHash() != null) {
            CiWorkflow workflow = workflowService.saveWorkflowAfterPush(repo, content, result.getCommitHash());
            System.out.println("Workflow SINGLE sauvegardé en base avec ID: " + workflow.getCiWorkflowId());
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "mode", "single",
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
        if (v.matches("^[vV]?\\d+(?:\\.\\d+){0,2}$")) return v.replaceFirst("^[vV]", "");
        java.util.regex.Matcher mx = java.util.regex.Pattern.compile("^(\\d+)\\.x$").matcher(v);
        if (mx.find()) return mx.group(1);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(?:\\^|~|>=|<=|>|<)?\\s*(\\d+)").matcher(v.replace("v",""));
        if (m.find()) return m.group(1);
        return "lts/*";
    }
}
