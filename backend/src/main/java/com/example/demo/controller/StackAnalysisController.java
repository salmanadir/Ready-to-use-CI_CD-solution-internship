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

            StackAnalysis analysis = stackDetectionService.analyzeRepository(
                    repo.getUrl(),
                    repo.getUser().getToken(),
                    repo.getDefaultBranch()
            );

            // 🔽 FLATTEN: sortir nodeVersion de projectDetails vers le top-level, et l'enlever de projectDetails
            if ("NODE_JS".equals(analysis.getStackType()) && analysis.getProjectDetails() != null) {
                Object raw = analysis.getProjectDetails().remove("nodeVersion"); // on la retire du projectDetails
                if (raw != null) {
                    analysis.setNodeVersion(raw.toString()); // on la met au top-level telle quelle (ex: "Latest")
                }
            }

            String analysisJson = objectMapper.writeValueAsString(analysis);
            repo.setTechnicalDetails(analysisJson);
            repoRepository.save(repo);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("analysis", analysis);
            response.put("message", "Analysis completed successfully");

            return ResponseEntity.ok(response);
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

            // 🔽 FLATTEN à l'affichage aussi (au cas où des anciennes données existent encore en base)
            if ("NODE_JS".equals(analysis.getStackType()) && analysis.getProjectDetails() != null) {
                if (analysis.getNodeVersion() == null || analysis.getNodeVersion().isBlank()) {
                    Object raw = analysis.getProjectDetails().get("nodeVersion");
                    if (raw != null) {
                        analysis.setNodeVersion(raw.toString());
                    }
                }
                // on n'écrit pas en base ici (GET), on ne fait que l'affichage
            }

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
            // Optionnel: permettre de mettre à jour la nodeVersion top-level
            if (updatedParameters.containsKey("nodeVersion")) {
                currentAnalysis.setNodeVersion((String) updatedParameters.get("nodeVersion"));
                // ne pas toucher projectDetails
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
