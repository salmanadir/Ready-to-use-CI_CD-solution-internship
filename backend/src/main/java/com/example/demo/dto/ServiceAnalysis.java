package com.example.demo.dto;  
  
import lombok.AllArgsConstructor;  
import lombok.NoArgsConstructor;  
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;  
import lombok.Data;  
import java.util.Map;  
  
@Data  
@AllArgsConstructor  
@NoArgsConstructor   
@JsonIgnoreProperties(ignoreUnknown = true)  
public class ServiceAnalysis {  
    private String id;                
    private String stackType;         
    private String workingDirectory;   
    private String buildTool;          
    private String language;        
    private Map<String, Object> projectDetails;   
    private String orchestrator;   
    private String javaVersion;  
      
    
    private String databaseType;  
    private String databaseName;  
}