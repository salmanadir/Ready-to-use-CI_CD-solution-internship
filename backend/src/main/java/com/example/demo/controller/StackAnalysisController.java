package com.example.demo.controller;

import java.util.Arrays;
import java.util.HashMap;
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

        String repoUrl   = repo.getUrl();
        String token     = repo.getUser().getToken();
        String defBranch = repo.getDefaultBranch();

        // 1) Détection multi-services (toujours)
        var services = stackDetectionService.analyzeAllServices(repoUrl, token);

        if (services == null || services.isEmpty()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "No recognizable service found (no pom.xml / build.gradle / package.json).");
            return ResponseEntity.ok(resp);
        }

        // 2) Choisir un "primary" (préférence backend Spring, sinon 1er)
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

        // 3) Mode single vs multi
        if (services.size() == 1) {
            payload.put("mode", "single");

            // On garde l’ancienne analyse "mono-service" pour compat UI existante
            StackAnalysis single = stackDetectionService.analyzeRepository(repoUrl, token, defBranch);
            payload.put("analysis", single); // <-- compat historique (ton UI l’attend)

            payload.put("message", "Single-service repository analyzed successfully");
        } else {
            payload.put("mode", "multi");
            payload.put("message", "Multi-service repository analyzed successfully");
        }

        // 4) Persistance d’un format unifié dans la colonne technicalDetails
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
    @PostMapping("/generate-services/{repoId}")  
        public ResponseEntity<Map<String, Object>> generateServices(@PathVariable Long repoId) {  
        try {  
            Repo repo = repoRepository.findById(repoId)  
                .orElseThrow(() -> new RuntimeException("Repository not found"));  
  
            Map<String, Object> services = stackDetectionService.generateStructuredServices(  
                repo.getUrl(),  
                repo.getUser().getToken(),  
                repo.getDefaultBranch()  
        );  
  
        return ResponseEntity.ok(services);  
        } catch (Exception e) {  
            Map<String, Object> errorResponse = new HashMap<>();  
            errorResponse.put("success", false);  
            errorResponse.put("message", "Error generating services: " + e.getMessage());  
        return ResponseEntity.badRequest().body(errorResponse);  
    }  
}
    @PutMapping("/repository/{repoId}/update-parameters")
    public ResponseEntity<Map<String, Object>> updateStackParameters(
            @PathVariable Long repoId,
            @RequestBody Map<String, Object> updatedParameters) {

        try {
            Repo repo = repoRepository.findById(repoId)
                    .orElseThrow(() -> new RuntimeException("Repository not found"));

            if (repo.getTechnicalDetails() == null || repo.getTechnicalDetails().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "No analysis available. Please analyze the repository first.");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            StackAnalysis currentAnalysis = objectMapper.readValue(repo.getTechnicalDetails(), StackAnalysis.class);

            validateParameters(currentAnalysis.getStackType(), updatedParameters);

            if (updatedParameters.containsKey("javaVersion")) {
                currentAnalysis.setJavaVersion((String) updatedParameters.get("javaVersion"));
            }
            if (updatedParameters.containsKey("workingDirectory")) {
                currentAnalysis.setWorkingDirectory((String) updatedParameters.get("workingDirectory"));
            }
            if (updatedParameters.containsKey("orchestrator")) {
                currentAnalysis.setOrchestrator((String) updatedParameters.get("orchestrator"));
            }
            
            if (updatedParameters.containsKey("nodeVersion")) {
                Map<String, Object> pd = currentAnalysis.getProjectDetails();
                if (pd == null) {
                    pd = new HashMap<>();
                    currentAnalysis.setProjectDetails(pd);
                }
                pd.put("nodeVersion", (String) updatedParameters.get("nodeVersion"));
            }

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

    @GetMapping("/repository/{repoId}/files")
    public ResponseEntity<Map<String, Object>> getRepositoryFiles(@PathVariable Long repoId) {
        try {
            Repo repo = repoRepository.findById(repoId)
                    .orElseThrow(() -> new RuntimeException("Repository not found"));

            if (repo.getTechnicalDetails() != null && !repo.getTechnicalDetails().isEmpty()) {
                StackAnalysis analysis = objectMapper.readValue(repo.getTechnicalDetails(), StackAnalysis.class);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("files", analysis.getFiles());
                response.put("repositoryInfo", Map.of(
                        "name", repo.getFullName(),
                        "url", repo.getUrl(),
                        "defaultBranch", repo.getDefaultBranch()
                ));

                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Repository not analyzed. Please analyze the repository first.");
                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error while retrieving files: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    private void validateParameters(String stackType, Map<String, Object> parameters) {
        if ("NODE_JS".equals(stackType) && parameters.containsKey("javaVersion")) {
            throw new RuntimeException("Java version is not applicable for a Node.js project");
        }

        String javaVersion = (String) parameters.get("javaVersion");
        if (javaVersion != null && !Arrays.asList("8", "11", "17", "21").contains(javaVersion)) {
            throw new RuntimeException("Unsupported Java version: " + javaVersion);
        }

        String orchestrator = (String) parameters.get("orchestrator");
        if (orchestrator != null && !Arrays.asList("github-actions", "gitlab-ci", "jenkins").contains(orchestrator)) {
            throw new RuntimeException("Unsupported orchestrator: " + orchestrator);
        }
    }
}
