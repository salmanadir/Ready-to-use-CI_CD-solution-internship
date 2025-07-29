package com.example.demo.controller;  
  
import com.example.demo.dto.StackAnalysis;  
import com.example.demo.model.Repo;  
import com.example.demo.service.StackDetectionService;  
import com.example.demo.repository.RepoRepository;  
import com.fasterxml.jackson.databind.ObjectMapper;  
import org.springframework.beans.factory.annotation.Autowired;  
import org.springframework.http.ResponseEntity;  
import org.springframework.web.bind.annotation.*;  
import java.util.Map;  
import java.util.HashMap;  
import java.util.Arrays;  

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
                .orElseThrow(() -> new RuntimeException("Repository non trouvé"));  
        
            StackAnalysis analysis = stackDetectionService.analyzeRepository(  
                repo.getUrl(),   
                repo.getUser().getToken(),   
                repo.getDefaultBranch()  
            );  
        
    
            String analysisJson = objectMapper.writeValueAsString(analysis);  
            repo.setTechnicalDetails(analysisJson);  
            repoRepository.save(repo);  
        
            Map<String, Object> response = new HashMap<>();  
            response.put("success", true);  
            response.put("analysis", analysis);  
            response.put("message", "Analyse terminée avec succès");  
            
            return ResponseEntity.ok(response);  
        } catch (Exception e) {  
            Map<String, Object> errorResponse = new HashMap<>();  
            errorResponse.put("success", false);  
            errorResponse.put("message", "Erreur lors de l'analyse: " + e.getMessage());  
            return ResponseEntity.badRequest().body(errorResponse);  
        }  
    }  
    

    @GetMapping("/repository/{repoId}")  
    public ResponseEntity<Map<String, Object>> getRepositoryAnalysis(@PathVariable Long repoId) {  
        try {  
            Repo repo = repoRepository.findById(repoId)  
                .orElseThrow(() -> new RuntimeException("Repository non trouvé"));  
            
            if (repo.getTechnicalDetails() == null || repo.getTechnicalDetails().isEmpty()) {  
                Map<String, Object> response = new HashMap<>();  
                response.put("success", false);  
                response.put("message", "Aucune analyse disponible pour ce repository");  
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
            errorResponse.put("message", "Erreur lors de la récupération: " + e.getMessage());  
            return ResponseEntity.badRequest().body(errorResponse);  
        }  
    }  
    
    @PutMapping("/repository/{repoId}/update-parameters")  
    public ResponseEntity<Map<String, Object>> updateStackParameters(  
            @PathVariable Long repoId,   
            @RequestBody Map<String, Object> updatedParameters) {  
        
        try {  
            Repo repo = repoRepository.findById(repoId)  
                .orElseThrow(() -> new RuntimeException("Repository non trouvé"));  
            
            if (repo.getTechnicalDetails() == null || repo.getTechnicalDetails().isEmpty()) {  
                Map<String, Object> errorResponse = new HashMap<>();  
                errorResponse.put("success", false);  
                errorResponse.put("message", "Aucune analyse disponible. Analysez d'abord le repository.");  
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
              
           
            String updatedJson = objectMapper.writeValueAsString(currentAnalysis);  
            repo.setTechnicalDetails(updatedJson);  
            repoRepository.save(repo);  
              
            Map<String, Object> response = new HashMap<>();  
            response.put("success", true);  
            response.put("analysis", currentAnalysis);  
            response.put("message", "Paramètres mis à jour avec succès");  
              
            return ResponseEntity.ok(response);  
        } catch (Exception e) {  
            Map<String, Object> errorResponse = new HashMap<>();  
            errorResponse.put("success", false);  
            errorResponse.put("message", "Erreur lors de la mise à jour: " + e.getMessage());  
            return ResponseEntity.badRequest().body(errorResponse);  
        }  
    }  

    @GetMapping("/repository/{repoId}/files")  
    public ResponseEntity<Map<String, Object>> getRepositoryFiles(@PathVariable Long repoId) {  
        try {  
            Repo repo = repoRepository.findById(repoId)  
                .orElseThrow(() -> new RuntimeException("Repository non trouvé"));  
              
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
                errorResponse.put("message", "Repository non analysé. Analysez d'abord le repository.");  
                return ResponseEntity.badRequest().body(errorResponse);  
            }  
        } catch (Exception e) {  
            Map<String, Object> errorResponse = new HashMap<>();  
            errorResponse.put("success", false);  
            errorResponse.put("message", "Erreur lors de la récupération des fichiers: " + e.getMessage());  
            return ResponseEntity.badRequest().body(errorResponse);  
        }  
    }  
      

    private void validateParameters(String stackType, Map<String, Object> parameters) {  
        if ("NODE_JS".equals(stackType) && parameters.containsKey("javaVersion")) {  
            throw new RuntimeException("Version Java non applicable pour un projet Node.js");  
        }  
          
        String javaVersion = (String) parameters.get("javaVersion");  
        if (javaVersion != null && !Arrays.asList("8", "11", "17", "21").contains(javaVersion)) {  
            throw new RuntimeException("Version Java non supportée: " + javaVersion);  
        }  
          
        String orchestrator = (String) parameters.get("orchestrator");  
        if (orchestrator != null && !Arrays.asList("github-actions", "gitlab-ci", "jenkins").contains(orchestrator)) {  
            throw new RuntimeException("Orchestrateur non supporté: " + orchestrator);  
        }  
    }  
}