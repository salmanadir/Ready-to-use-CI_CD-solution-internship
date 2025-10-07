package com.example.demo.controller;

import com.example.demo.ci.template.TemplateRenderer;
import com.example.demo.dto.ContainerPlan;
import com.example.demo.dto.StackAnalysis;
import com.example.demo.dto.WorkflowGenerationRequest;
import com.example.demo.model.CiWorkflow;
import com.example.demo.model.DockerComposeHistory;
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
    @Autowired private DockerfileHistoryService dockerfileHistoryService;
    @Autowired private DockerComposeHistoryService dockerComposeHistoryService;
    @Autowired private DockerfileGenerationService dockerfileGenerationService;



    // NEW: service dédié au compose de prod
    @Autowired private DockerComposeService dockerComposeService;

    // =====================================================================================
    // A) PREVIEW (ne push rien) — SINGLE ou MULTI selon request.services
    //    -> ne traite QUE Dockerfile/CI 
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

        // ---- MULTI: return one enriched plan per service with preview filled
if (request.getServices()!=null && !request.getServices().isEmpty()) {

    List<Map<String,Object>> enriched = new ArrayList<>();

    for (Map<String,Object> svc : request.getServices()) {
        // 1) Rebuild the service info
        StackAnalysis info = StackAnalysis.fromMap(svc);
        if (info == null) continue;

        // Keep EXACTLY the WD coming from /analyze
        String svcWd = (info.getWorkingDirectory()==null || info.getWorkingDirectory().isBlank())
                ? "." : info.getWorkingDirectory();

        // 2) Build a per-service container plan
        ContainerPlan p = containerizationService.plan(
                repo.getUrl(), token,
                (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                        ? repo.getFullName() : docker.getImageNameOverride(),
                info, docker.getImageNameOverride(), docker.getRegistry()
        );

        // 3) Choose path and compute preview content
        String path = (p.getDockerfilePath()==null || p.getDockerfilePath().isBlank())
                ? defaultDockerfilePath(svcWd)
                : p.getDockerfilePath();

        String previewContent;
        String previewSource;

        if (!p.isShouldGenerateDockerfile()) {
            // Try reading existing file from GitHub
            String existing = null;
            try { existing = gitHubService.getFileContent(repo.getUrl(), token, path); } catch (Exception ignore) {}
            if (existing != null && !existing.isBlank()) {
                previewContent = existing;
                previewSource  = "existing";
            } else {
                // Safe fallback
                previewContent = dockerfileGenerationService.generate(info);
                previewSource  = "generated";
            }
        } else {
            // No Dockerfile yet → generate a draft
            previewContent = dockerfileGenerationService.generate(info);
            previewSource  = "generated";
        }

        Map<String,Object> m = new HashMap<>();
        m.put("workingDirectory", svcWd);
        m.put("dockerfilePath", path);
        m.put("shouldGenerateDockerfile", p.isShouldGenerateDockerfile());
        m.put("previewDockerfileContent", previewContent);
        m.put("previewSource", previewSource);

        // optional but handy in the UI
        m.put("registry", p.getRegistry()!=null ? p.getRegistry() : "ghcr.io");
        m.put("imageName", p.getImageName());
        m.put("dockerContext", p.getDockerContext());

        enriched.add(m);
    }

    boolean readyForCi = enriched.stream().noneMatch(mp ->
            Boolean.TRUE.equals(mp.get("shouldGenerateDockerfile")));

    return ResponseEntity.ok(Map.of(
            "success", true,
            "mode", "multi",
            "readyForCi", readyForCi,
            "plans", enriched
    ));
}


        // ----------------------- MODE SINGLE -----------------------
        StackAnalysis info = request.getTechStackInfo();
        if (info == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null"));
        }

        ContainerPlan containerPlan = containerizationService.plan(
                repo.getUrl(), token,
                (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                        ? repo.getFullName() : docker.getImageNameOverride(),
                info, docker.getImageNameOverride(), docker.getRegistry()
        );

        // enrichir: toujours mettre quelque chose dans previewDockerfileContent
        if (!containerPlan.isShouldGenerateDockerfile()) {
            String path = containerPlan.getDockerfilePath();
            if (path == null || path.isBlank()) {
                path = defaultDockerfilePath(info.getWorkingDirectory());
                containerPlan.setDockerfilePath(path);
            }
            try {
                String existing = gitHubService.getFileContent(repo.getUrl(), token, path);
                if (existing != null) {
                    containerPlan.setExistingDockerfileContent(existing);
                    containerPlan.setPreviewDockerfileContent(existing);
                    containerPlan.setPreviewSource("existing");
                }
            } catch (Exception ignore) {}
        } else {
            String gen = dockerfileGenerationService.generate(info);
            containerPlan.setGeneratedDockerfileContent(gen);
            containerPlan.setProposedDockerfileContent(gen);
            containerPlan.setPreviewDockerfileContent(gen);
            containerPlan.setPreviewSource("generated");
            if (containerPlan.getDockerfilePath()==null || containerPlan.getDockerfilePath().isBlank()) {
                containerPlan.setDockerfilePath(defaultDockerfilePath(info.getWorkingDirectory()));
            }
        }

        boolean readyForCi = !containerPlan.isShouldGenerateDockerfile();

        Map<String,Object> out = new HashMap<>();
        out.put("success", true);
        out.put("mode", "single");
        out.put("readyForCi", readyForCi);
        out.put("containerPlan", containerPlan);
        return ResponseEntity.ok(out);

    } catch (Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
    }
}
private String defaultDockerfilePath(String workingDir) {
    String wd = (workingDir==null || workingDir.isBlank()) ? "." : workingDir;
    if (".".equals(wd)) return "Dockerfile";
    String clean = wd.replaceFirst("^\\./","").replaceAll("/+$","");
    return clean + "/Dockerfile";
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
                        "success", true,
                        "mode", "single",
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
                    "success", true,
                    "mode", "single",
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
    // C) COMPOSE PROD — PREVIEW (scan + proposition si absent)
    // =====================================================================================
    @PostMapping("/compose/prod/preview")
    public ResponseEntity<?> previewComposeProd(@RequestBody WorkflowGenerationRequest request,
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

            // éventuels dossiers de services fournis par la requête (pour scanner aussi ces chemins)
            List<String> serviceDirs = new ArrayList<>();
            if (request.getServices()!=null) {
                for (Map<String,Object> s : request.getServices()) {
                    Object wd = s.get("workingDirectory");
                    if (wd!=null) serviceDirs.add(String.valueOf(wd));
                }
            } else if (request.getTechStackInfo()!=null && request.getTechStackInfo().getWorkingDirectory()!=null) {
                serviceDirs.add(request.getTechStackInfo().getWorkingDirectory());
            }

            var existing = dockerComposeService.findComposeFiles(repo.getUrl(), token, serviceDirs);
            if (!existing.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "hasCompose", true,
                        "paths", existing
                ));
            }

            String imageBase = repo.getFullName().toLowerCase(); // owner/repo
            String preview = dockerComposeService.buildPreviewOrThrow(
                    repo.getUrl(), token, repo.getDefaultBranch(), imageBase
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "hasCompose", false,
                    "suggestedPath", "docker-compose.yml",
                    "composePreview", preview
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =====================================================================================
// D) COMPOSE PROD — APPLY (push le compose si absent)
// =====================================================================================
@PostMapping("/compose/prod/apply")
@Transactional
public ResponseEntity<?> applyComposeProd(@RequestParam(value="path", required=false) String path,
                                          @RequestBody(required=false) WorkflowGenerationRequest request,
                                          Authentication authentication) {
    try {
        var repo = requireAuthAndRepo(authentication, request!=null?request.getRepoId():null);
        if (repo == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false, "message", "Authentication required or repo not found / not owned"
        ));
        String token = repo.getUser().getToken();
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "GitHub token not found for user"));
        }
        gitHubService.setCurrentToken(token);

        // déjà présent quelque part ?
        var existing = dockerComposeService.findComposeFiles(repo.getUrl(), token, null);
        if (!existing.isEmpty()) {
            // ✅ NOUVEAU: Historiser les fichiers compose EXISTANTS détectés
            for (String existingPath : existing) {
                try {
                    String existingContent = gitHubService.getFileContent(repo.getUrl(), token, existingPath);
                    if (existingContent != null) {
                        // Déterminer le mode selon la requête
                        DockerComposeHistory.Mode mode = (request != null && request.getServices() != null && !request.getServices().isEmpty()) 
                            ? DockerComposeHistory.Mode.MULTI 
                            : DockerComposeHistory.Mode.SINGLE;
                        
                        // Extraire les noms de services si possible (optionnel)
                        Collection<String> serviceNames = extractServiceNamesFromRequest(request);
                        
                        dockerComposeHistoryService.recordExisting(repo, existingPath, existingContent, mode, serviceNames);
                    }
                } catch (Exception e) {
                    System.err.println("Erreur lors de l'historisation du compose existant " + existingPath + ": " + e.getMessage());
                }
            }
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Compose already present — nothing to apply",
                    "paths", existing
            ));
        }

        String imageBase = repo.getFullName().toLowerCase();
        String composeYaml = dockerComposeService.buildPreviewOrThrow(
                repo.getUrl(), token, repo.getDefaultBranch(), imageBase
        );

        String filePath = (path==null || path.isBlank()) ? "docker-compose.yml" : path;
        GitHubService.PushResult pr = gitHubService.pushWorkflowToGitHub(
                token, repo.getFullName(), repo.getDefaultBranch(),
                filePath, composeYaml,
                GitHubService.FileHandlingStrategy.UPDATE_IF_EXISTS
        );

        // ✅ NOUVEAU: Historiser le compose GÉNÉRÉ après push réussi
        if (pr != null) {
            // Déterminer le mode selon la requête
            DockerComposeHistory.Mode mode = (request != null && request.getServices() != null && !request.getServices().isEmpty()) 
                ? DockerComposeHistory.Mode.MULTI 
                : DockerComposeHistory.Mode.SINGLE;
            
            // Extraire les noms de services si possible
            Collection<String> serviceNames = extractServiceNamesFromRequest(request);
            
            dockerComposeHistoryService.recordGenerated(repo, filePath, composeYaml, mode, serviceNames, pr);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", pr.getMessage(),
                "commitHash", pr.getCommitHash(),
                "filePath", pr.getFilePath()
        ));

    } catch (IOException e) {
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "IO Error: " + e.getMessage()));
    } catch (Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
    }
}

// ✅ NOUVEAU: Méthode helper pour extraire les noms de services de la requête
private Collection<String> extractServiceNamesFromRequest(WorkflowGenerationRequest request) {
    if (request == null) return null;
    
    List<String> serviceNames = new ArrayList<>();
    
    // Mode MULTI : extraire des services
    if (request.getServices() != null && !request.getServices().isEmpty()) {
        for (Map<String, Object> service : request.getServices()) {
            // Essayer d'extraire un nom de service depuis workingDirectory ou un champ "name"
            Object workingDir = service.get("workingDirectory");
            Object name = service.get("name");
            Object serviceName = service.get("serviceName");
            
            if (serviceName != null) {
                serviceNames.add(serviceName.toString());
            } else if (name != null) {
                serviceNames.add(name.toString());
            } else if (workingDir != null && !workingDir.toString().equals(".")) {
                // Utiliser le dernier segment du workingDirectory comme nom de service
                String dir = workingDir.toString();
                String[] parts = dir.split("/");
                serviceNames.add(parts[parts.length - 1]);
            }
        }
    }
    // Mode SINGLE : nom basé sur le repo ou workingDirectory
    else if (request.getTechStackInfo() != null) {
        String workingDir = request.getTechStackInfo().getWorkingDirectory();
        if (workingDir != null && !workingDir.equals(".")) {
            String[] parts = workingDir.split("/");
            serviceNames.add(parts[parts.length - 1]);
        } else {
            // Utiliser le nom du repo comme nom de service par défaut
            // Note: il faudrait passer le repo en paramètre pour obtenir son nom
            serviceNames.add("app"); // nom générique par défaut
        }
    }
    
    return serviceNames.isEmpty() ? null : serviceNames;
}
// =====================================================================================
// CI — PREVIEW (ne push rien) — SINGLE ou MULTI
// =====================================================================================
@PostMapping("/ci/preview")
public ResponseEntity<?> previewCi(@RequestBody WorkflowGenerationRequest request,
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

        // -------- MODE MULTI
if (request.getServices() != null && !request.getServices().isEmpty()) {
    List<Map<String, Object>> previews = new ArrayList<>();

    // 👇 NEW: compteurs par outil
    Map<String, Integer> toolCounters = new HashMap<>();

    for (Map<String, Object> svc : request.getServices()) {
        StackAnalysis info = StackAnalysis.fromMap(svc);
        if (info == null) continue;

        // Construit le YAML (et vérifie que le Dockerfile est bien appliqué)
        Map<String, Object> built = buildCiContentAndPath(repo, info, docker, token);
        String content  = (String) built.get("content");

        // 👇 NEW: nom de fichier basé sur l'outil (maven/gradle/npm→node) + suffixes 2,3...
        String buildToolLower = (info.getBuildTool() == null) ? "" : info.getBuildTool().toLowerCase();
        String fileName = nextCiFileName(buildToolLower, toolCounters);
        String filePath = workflowsPath(fileName);

        // Lire le fichier existant à CE chemin
        String existing = null;
        try { existing = gitHubService.getFileContent(repo.getUrl(), token, filePath); } catch (Exception ignore) {}

        String status = (existing == null) ? "NOT_FOUND" : (existing.equals(content) ? "IDENTICAL" : "DIFFERENT");

        previews.add(Map.of(
                "service", info.getWorkingDirectory(),
                "filePath", filePath,           // 👈 chemin imposé
                "status", status,
                "intendedStrategy", request.getFileHandlingStrategy() != null
                        ? request.getFileHandlingStrategy().name()
                        : "UPDATE_IF_EXISTS",
                "content", content
        ));
    }

    return ResponseEntity.ok(Map.of(
            "success", true,
            "mode", "multi",
            "previews", previews
    ));
}

        // -------- MODE SINGLE
        StackAnalysis info = request.getTechStackInfo();
        if (info == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "TechStackInfo is null in request"));
        }

        Map<String, Object> built = buildCiContentAndPath(repo, info, docker, token);
        String filePath = (String) built.get("filePath");
        String content  = (String) built.get("content");

        String existing = null;
        try {
            existing = gitHubService.getFileContent(repo.getUrl(), token, filePath);
        } catch (Exception ignore) {}

        String status = (existing == null) ? "NOT_FOUND" : (existing.equals(content) ? "IDENTICAL" : "DIFFERENT");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "mode", "single",
                "filePath", filePath,
                "status", status,
                "intendedStrategy", request.getFileHandlingStrategy() != null ? request.getFileHandlingStrategy().name() : "UPDATE_IF_EXISTS",
                "content", content
        ));

    } catch (IllegalStateException ie) {
        // cas Dockerfile non appliqué -> 428 pour cohérence avec /generate
        return ResponseEntity.status(428).body(Map.of(
                "success", false,
                "message", ie.getMessage(),
                "hintPreviewDocker", "/api/workflows/docker/preview",
                "hintApplyDocker", "/api/workflows/dockerfile/apply"
        ));
    } catch (Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
    }
}

    // =====================================================================================
    // E) GENERATE CI (push) — SINGLE ou MULTI
    //     
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

            // -------- MODE MULTI
if (request.getServices() != null && !request.getServices().isEmpty()) {
    List<Map<String, Object>> results = new ArrayList<>();

    // 👇 NEW: compteurs par outil pour générer des noms stables et sans collisions
    Map<String, Integer> toolCounters = new HashMap<>();

    for (Map<String,Object> svc : request.getServices()) {
        StackAnalysis info = StackAnalysis.fromMap(svc);
        if (info == null) continue;

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

        // 👇 NEW: impose un nom par outil, avec suffixes si plusieurs services du même outil
        String fileName = nextCiFileName(buildToolLower, toolCounters);
        String filePath = workflowsPath(fileName);

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
                "filePath", filePath,           // 👈 renvoyer le chemin réellement poussé
                "commitHash", pr.getCommitHash()
        ));
    }

    return ResponseEntity.ok(Map.of(
            "success", true,
            "mode", "multi",
            "workflows", results
    ));
}


            // -------- MODE SINGLE
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
// Helper commun (build du YAML + path cible) — SINGLE
// ======================
private Map<String, Object> buildCiContentAndPath(
        Repo repo,
        StackAnalysis info,
        WorkflowGenerationRequest.DockerOptions docker,
        String token
) throws Exception {

    // 1) Plan de containerisation
    ContainerPlan containerPlan = containerizationService.plan(
            repo.getUrl(), token,
            (docker.getImageNameOverride() == null || docker.getImageNameOverride().isBlank())
                    ? repo.getFullName() : docker.getImageNameOverride(),
            info, docker.getImageNameOverride(), docker.getRegistry()
    );

    if (containerPlan.isShouldGenerateDockerfile()) {
        // même règle que /generate : on n'autorise pas la génération CI tant que le Dockerfile n'est pas appliqué
        throw new IllegalStateException(
                "Dockerfile not applied yet. Preview + apply the Dockerfile before generating CI."
        );
    }

    // 2) Template selon le stack
    String templatePath = templateService.getTemplatePath(info);

    // 3) Placeholders
    Map<String, String> replacements = new HashMap<>();
    String workingDir = (info.getWorkingDirectory() == null || info.getWorkingDirectory().isBlank())
            ? "." : info.getWorkingDirectory();
    replacements.put("workingDirectory", workingDir);

    String buildToolLower = (info.getBuildTool() == null) ? "" : info.getBuildTool().toLowerCase();
    if ("maven".equals(buildToolLower) || "gradle".equals(buildToolLower)) {
        replacements.put("javaVersion",
                (info.getJavaVersion() != null && !info.getJavaVersion().isBlank()) ? info.getJavaVersion() : "17");
    } else if ("npm".equals(buildToolLower)) {
        String nodeVersion = null;
        if (info.getProjectDetails() != null) {
            Object v = info.getProjectDetails().get("nodeVersion");
            nodeVersion = (v != null) ? v.toString() : null;
        }
        replacements.put("nodeVersion", resolveNodeVersionForActions(nodeVersion));
    }

    // placeholders du plan (image, registry, docker context/path, etc.)
    replacements.putAll(containerPlan.placeholders());

    // 4) YAML final
    String content = templateRenderer.renderTemplate(templatePath, replacements);

    // 5) Chemin cible
    String fileName = switch (buildToolLower) {
        case "maven" -> "maven-ci.yml";
        case "gradle" -> "gradle-ci.yml";
        case "npm" -> "npm-ci.yml";
        default -> "ci.yml";
    };
    String filePath = ".github/workflows/" + fileName;

    Map<String, Object> out = new HashMap<>();
    out.put("content", content);
    out.put("filePath", filePath);
    out.put("containerPlan", containerPlan);
    out.put("buildTool", buildToolLower);
    return out;
}

    // ======================
    // Helpers (privés)
    // ======================
    // Mappe "npm" -> "node", sinon retourne l'outil en minuscules.
private static String toolSlug(String buildToolLower) {
    if (buildToolLower == null) return "generic";
    String t = buildToolLower.toLowerCase();
    return switch (t) {
        case "npm" -> "node";
        default -> t.replaceAll("[^a-z0-9]+", "-");
    };
}

/** Incrémente un compteur par outil pour générer ci-generated-<tool>.yml, ci-generated-<tool>-2.yml, ... */
private static String nextCiFileName(String buildToolLower, Map<String, Integer> counters) {
    String slug = toolSlug(buildToolLower);
    int n = counters.merge(slug, 1, Integer::sum);
    return (n == 1) ? ("ci-generated-" + slug + ".yml")
                    : ("ci-generated-" + slug + "-" + n + ".yml");
}

private static String workflowsPath(String fileName) {
    return ".github/workflows/" + fileName;
}

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