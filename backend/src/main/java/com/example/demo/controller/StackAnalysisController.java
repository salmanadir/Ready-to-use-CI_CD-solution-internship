package com.example.demo.controller;  
  
import java.util.Arrays;  
import java.util.HashMap;  
import java.util.List;  
import java.util.Map;  
  
import org.springframework.beans.factory.annotation.Autowired;  
import org.springframework.http.ResponseEntity;  
import org.springframework.web.bind.annotation.CrossOrigin;  
import org.springframework.web.bind.annotation.GetMapping;  
import org.springframework.web.bind.annotation.PathVariable;  
import org.springframework.web.bind.annotation.PostMapping;  
import org.springframework.web.bind.annotation.PutMapping;  
import org.springframework.web.bind.annotation.RequestBody;  
import org.springframework.web.bind.annotation.RequestMapping;  
import org.springframework.web.bind.annotation.RestController;  
  
import com.example.demo.dto.StackAnalysis;  
import com.example.demo.model.Repo;  
import com.example.demo.repository.RepoRepository;  
import com.example.demo.service.StackDetectionService;  
import com.fasterxml.jackson.databind.ObjectMapper;  
  
@RestController  
@RequestMapping("/api/stack-analysis")  
@CrossOrigin(origins = "*")  
public class StackAnalysisController {  
  
    @Autowired  
    private StackDetectionService stackDetectionService;  
  
    @Autowired  
    private RepoRepository repoRepository;  
  
    private final ObjectMapper objectMapper = new ObjectMapper();  
  
    @PostMapping("/analyze/{repoId}")  
    public ResponseEntity<Map<String, Object>> analyzeRepository(@PathVariable Long repoId) {  
        try {  
            Repo repo = repoRepository.findById(repoId)  
                    .orElseThrow(() -> new RuntimeException("Repository not found"));  
  
            String repoUrl = repo.getUrl();  
            String token = repo.getUser().getToken();  
            String defBranch = repo.getDefaultBranch();  
  
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
  
            // Récupérer l'analyse complète (pas seulement StackAnalysis)  
            Map<String, Object> currentAnalysis = objectMapper.readValue(repo.getTechnicalDetails(), Map.class);  
  
            // Valider les paramètres avant mise à jour  
            validateAllParameters(currentAnalysis, request);  
  
            // Gérer les modifications selon le mode  
            if ("multi".equals(currentAnalysis.get("mode"))) {  
                updateMultiServiceParameters(currentAnalysis, request);  
            } else if ("single".equals(currentAnalysis.get("mode"))) {  
                updateSingleServiceParameters(currentAnalysis, request);  
            }  
  
            // Sauvegarder les modifications  
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
                // Mettre à jour au niveau global pour la base de données  
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
                // Mettre à jour directement dans l'analyse single  
                singleAnalysis.put(key, updates.get(key));  
            }  
        }  
    }  
  
    private void updateNestedField(Map<String, Object> target, String fieldPath, Object value) {  
        String[] parts = fieldPath.split("\\.");  
        Map<String, Object> current = target;  
  
        for (int i = 0; i < parts.length - 1; i++) {  
            if (!current.containsKey(parts[i])) {  
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
        // Validation Java Version  
        if ("NODE_JS".equals(stackType) && updatedParameters.containsKey("javaVersion")) {  
            throw new RuntimeException("Java version is not applicable for a Node.js project");  
        }  
  
        String javaVersion = (String) updatedParameters.get("javaVersion");  
        if (javaVersion != null && !Arrays.asList("8", "11", "17", "21").contains(javaVersion)) {  
            throw new RuntimeException("Unsupported Java version: " + javaVersion);  
        }  
  
        // Validation Build Tool  
        String buildTool = (String) updatedParameters.get("buildTool");  
        if (buildTool != null && !Arrays.asList("Maven", "Gradle", "npm").contains(buildTool)) {  
            throw new RuntimeException("Unsupported build tool: " + buildTool);  
        }  
  
        // Validation Database Type  
        String databaseType = (String) updatedParameters.get("databaseType");  
        if (databaseType != null && !Arrays.asList("PostgreSQL", "MySQL", "MongoDB", "H2", "NONE", "Detected (Spring Boot with JPA)").contains(databaseType)) {  
            throw new RuntimeException("Unsupported database type: " + databaseType);  
        }  
  
        // Validation Framework  
        String framework = (String) updatedParameters.get("framework");  
        if (framework != null && stackType.equals("NODE_JS")) {  
            List<String> validFrameworks = Arrays.asList("React", "Vue.js", "Angular", "Express.js", "Next.js", "Vanilla Node.js");  
            if (!validFrameworks.contains(framework)) {  
                throw new RuntimeException("Unsupported Node.js framework: " + framework);  
            }  
        }  
  
        // Validation Working Directory  
        String workingDirectory = (String) updatedParameters.get("workingDirectory");  
        if (workingDirectory != null && workingDirectory.trim().isEmpty()) {  
            throw new RuntimeException("Working directory cannot be empty");  
        }  
  
        // Validation Node Version  
        String nodeVersion = (String) updatedParameters.get("nodeVersion");  
        if (nodeVersion != null && stackType.equals("NODE_JS")) {  
            if (!nodeVersion.matches("\\d+(\\.\\d+)*") && !"Latest".equals(nodeVersion)) {  
                throw new RuntimeException("Invalid Node.js version format: " + nodeVersion);  
            }  
        }  
    }  
  
    // Autres méthodes existantes...  
    @GetMapping("/repository/{repoId}")  
    public ResponseEntity<Map<String, Object>> getRepositoryAnalysis(@PathVariable Long repoId) {  
        try {  
            Repo repo = repoRepository.findById(repoId)  
                    .orElseThrow(() -> new RuntimeException("Repository not found"));  
  
            if (repo.getTechnicalDetails() == null || repo.getTechnicalDetails().isEmpty()) {  
                Map<String, Object> response = new HashMap<>();  
                response.put("success", false);  
                response.put("message", "No analysis available for this repository");  
                return ResponseEntity.ok(response);  
            }  
  
            StackAnalysis analysis = objectMapper.readValue(repo.getTechnicalDetails(), StackAnalysis.class);  
  
            Map<String, Object> response = new HashMap<>();  
            response.put("success", true);  
            response.put("analysis", analysis);  
  
            return ResponseEntity.ok(response);  
        } catch (Exception e) {  
            Map<String, Object> errorResponse = new HashMap<>();  
            errorResponse.put("success", false);  
            errorResponse.put("message", "Error while retrieving analysis: " + e.getMessage());  
            return ResponseEntity.badRequest().body(errorResponse);  
        }  
    }  
  
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
}