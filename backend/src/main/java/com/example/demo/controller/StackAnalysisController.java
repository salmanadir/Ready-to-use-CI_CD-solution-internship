package com.example.demo.controller;

import com.example.demo.dto.StackAnalysis;
import com.example.demo.model.Repo;
import com.example.demo.repository.RepoRepository;
import com.example.demo.service.StackDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Endpoints alignés avec le frontend :
 *  - POST /api/stack-analysis/analyze/{repoId}
 *  - GET  /api/stack-analysis/repository/{repoId}/all-files
 *  - PUT  /api/stack-analysis/repository/{repoId}/update-parameters
 *
 * Le format renvoyé par /analyze est plat (mode/services/analysis au niveau racine),
 * afin d’éviter la redondance et de correspondre à validateAndNormalizeAnalysis côté front.
 */
@RestController
@RequestMapping("/api/stack-analysis")
@CrossOrigin(origins = "*")
public class StackAnalysisController {

    @Autowired
    private StackDetectionService stackDetectionService;

    @Autowired
    private RepoRepository repoRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Lance l’analyse, renvoie { success, mode, services, primaryServiceId, defaultBranch, analysis? }
     * et persiste ce payload dans repo.technicalDetails (JSON).
     */
    @PostMapping("/analyze/{repoId}")
    public ResponseEntity<Map<String, Object>> analyzeRepository(@PathVariable Long repoId) {
        try {
            Repo repo = repoRepository.findById(repoId)
                    .orElseThrow(() -> new RuntimeException("Repository not found"));

            String repoUrl    = repo.getUrl();
            String token      = repo.getUser().getToken();
            String defBranch  = repo.getDefaultBranch();

            var services = stackDetectionService.analyzeAllServices(repoUrl, token);
            if (services == null || services.isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "No recognizable service found (no pom.xml / build.gradle / package.json).");
                return ResponseEntity.ok(resp);
            }

            String primaryServiceId = services.stream()
                    .filter(s -> s.getStackType().contains("SPRING_BOOT"))
                    .map(s -> s.getId())
                    .findFirst()
                    .orElse(services.get(0).getId());

            Map<String, Object> payload = new HashMap<>();
            payload.put("success", true);
            payload.put("services", services);
            payload.put("primaryServiceId", primaryServiceId);
            payload.put("defaultBranch", defBranch);

            if (services.size() == 1) {
                payload.put("mode", "single");
                StackAnalysis single = stackDetectionService.analyzeRepository(repoUrl, token, defBranch);
                payload.put("analysis", single);
                payload.put("message", "Single-service repository analyzed successfully");
            } else {
                payload.put("mode", "multi");
                payload.put("message", "Multi-service repository analyzed successfully");
            }

            String detailsJson = objectMapper.writeValueAsString(payload);
            repo.setTechnicalDetails(detailsJson);
            repoRepository.save(repo);

            return ResponseEntity.ok(payload);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error during analysis: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Retourne la liste des fichiers (pour l’arbre côté front).
     */
    @GetMapping("/repository/{repoId}/all-files")
    public ResponseEntity<Map<String, Object>> getAllRepositoryFiles(@PathVariable Long repoId) {
        try {
            Repo repo = repoRepository.findById(repoId)
                    .orElseThrow(() -> new RuntimeException("Repository not found"));

            List<String> allFiles = stackDetectionService.getAllRepositoryFiles(
                    repo.getUrl(),
                    repo.getUser().getToken(),
                    repo.getDefaultBranch()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("files", allFiles);
            response.put("repositoryInfo", Map.of(
                    "name", repo.getFullName(),
                    "url", repo.getUrl(),
                    "defaultBranch", repo.getDefaultBranch()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error while retrieving all files: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Met à jour les paramètres d’analyse (patch partiel) puis persiste.
     * Le body est un map de clés plates (ex: services.0.buildTool, analysis.javaVersion, databaseType, …)
     * comme envoyé par le frontend.
     */
    @PutMapping("/repository/{repoId}/update-parameters")
    public ResponseEntity<Map<String, Object>> updateStackParameters(
            @PathVariable Long repoId,
            @RequestBody Map<String, Object> request) {

        try {
            Repo repo = repoRepository.findById(repoId)
                    .orElseThrow(() -> new RuntimeException("Repository not found"));

            if (repo.getTechnicalDetails() == null || repo.getTechnicalDetails().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "No analysis available. Please analyze the repository first.");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // on récupère le payload complet (celui sauvegardé par /analyze)
            @SuppressWarnings("unchecked")
            Map<String, Object> currentAnalysis = objectMapper.readValue(repo.getTechnicalDetails(), Map.class);

            // Valider toutes les entrées avant application
            validateAllParameters(currentAnalysis, request);

            // Appliquer selon le mode
            if ("multi".equals(currentAnalysis.get("mode"))) {
                updateMultiServiceParameters(currentAnalysis, request);
            } else if ("single".equals(currentAnalysis.get("mode"))) {
                updateSingleServiceParameters(currentAnalysis, request);
            }

            // Sauvegarde
            String updatedJson = objectMapper.writeValueAsString(currentAnalysis);
            repo.setTechnicalDetails(updatedJson);
            repoRepository.save(repo);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("analysis", currentAnalysis);
            response.put("message", "Parameters updated successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error while updating parameters: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ---------- Helpers de mise à jour / validation ----------

    @SuppressWarnings("unchecked")
    private void updateMultiServiceParameters(Map<String, Object> analysis, Map<String, Object> updates) {
        List<Map<String, Object>> services = (List<Map<String, Object>>) analysis.get("services");

        for (String key : updates.keySet()) {
            if (key.startsWith("services.")) {
                String[] pathParts = key.split("\\.");
                int serviceIndex = Integer.parseInt(pathParts[1]);
                String fieldPath = String.join(".", Arrays.copyOfRange(pathParts, 2, pathParts.length));
                updateNestedField(services.get(serviceIndex), fieldPath, updates.get(key));
            } else if (key.equals("databaseType") || key.equals("databaseName")) {
                analysis.put(key, updates.get(key));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateSingleServiceParameters(Map<String, Object> analysis, Map<String, Object> updates) {
        Map<String, Object> singleAnalysis = (Map<String, Object>) analysis.get("analysis");

        for (String key : updates.keySet()) {
            if (key.startsWith("analysis.")) {
                String fieldPath = key.substring("analysis.".length());
                updateNestedField(singleAnalysis, fieldPath, updates.get(key));
            } else if (key.equals("databaseType") || key.equals("databaseName")) {
                singleAnalysis.put(key, updates.get(key));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateNestedField(Map<String, Object> target, String fieldPath, Object value) {
        String[] parts = fieldPath.split("\\.");
        Map<String, Object> current = target;

        for (int i = 0; i < parts.length - 1; i++) {
            if (!current.containsKey(parts[i]) || !(current.get(parts[i]) instanceof Map)) {
                current.put(parts[i], new HashMap<String, Object>());
            }
            current = (Map<String, Object>) current.get(parts[i]);
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private void validateAllParameters(Map<String, Object> analysis, Map<String, Object> updates) {
        String mode = (String) analysis.get("mode");

        if ("multi".equals(mode)) {
            List<Map<String, Object>> services = (List<Map<String, Object>>) analysis.get("services");
            for (String key : updates.keySet()) {
                if (key.startsWith("services.")) {
                    String[] pathParts = key.split("\\.");
                    int serviceIndex = Integer.parseInt(pathParts[1]);
                    String stackType = (String) services.get(serviceIndex).get("stackType");
                    validateParameters(stackType, Map.of(pathParts[pathParts.length - 1], updates.get(key)));
                }
            }
        } else if ("single".equals(mode)) {
            Map<String, Object> singleAnalysis = (Map<String, Object>) analysis.get("analysis");
            String stackType = (String) singleAnalysis.get("stackType");
            validateParameters(stackType, updates);
        }
    }

    private void validateParameters(String stackType, Map<String, Object> updatedParameters) {
        if ("NODE_JS".equals(stackType) && updatedParameters.containsKey("javaVersion")) {
            throw new RuntimeException("Java version is not applicable for a Node.js project");
        }

        String javaVersion = (String) updatedParameters.get("javaVersion");
        if (javaVersion != null && !Arrays.asList("8", "11", "17", "21").contains(javaVersion)) {
            throw new RuntimeException("Unsupported Java version: " + javaVersion);
        }

        String buildTool = (String) updatedParameters.get("buildTool");
        if (buildTool != null && !Arrays.asList("Maven", "Gradle", "npm").contains(buildTool)) {
            throw new RuntimeException("Unsupported build tool: " + buildTool);
        }

        String databaseType = (String) updatedParameters.get("databaseType");
        if (databaseType != null && !Arrays.asList("PostgreSQL", "MySQL", "MongoDB", "H2", "NONE", "Detected (Spring Boot with JPA)").contains(databaseType)) {
            throw new RuntimeException("Unsupported database type: " + databaseType);
        }

        String framework = (String) updatedParameters.get("framework");
        if (framework != null && "NODE_JS".equals(stackType)) {
            List<String> validFrameworks = Arrays.asList("React", "Vue.js", "Angular", "Express.js", "Next.js", "Vanilla Node.js");
            if (!validFrameworks.contains(framework)) {
                throw new RuntimeException("Unsupported Node.js framework: " + framework);
            }
        }

        String workingDirectory = (String) updatedParameters.get("workingDirectory");
        if (workingDirectory != null && workingDirectory.trim().isEmpty()) {
            throw new RuntimeException("Working directory cannot be empty");
        }

        String nodeVersion = (String) updatedParameters.get("nodeVersion");
        if (nodeVersion != null && "NODE_JS".equals(stackType)) {
            if (!nodeVersion.matches("\\d+(\\.\\d+)*") && !"Latest".equals(nodeVersion)) {
                throw new RuntimeException("Invalid Node.js version format: " + nodeVersion);
            }
        }
    }
}
