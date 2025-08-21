// src/main/java/com/example/demo/dto/ServiceAnalysis.java
package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Map;

@Data
@AllArgsConstructor
public class ServiceAnalysis {
  private String id;               // ex: backend-0, frontend-1
  private String stackType;       
  private String workingDirectory; 
  private String buildTool;        
  private String language;      
  private Map<String, Object> projectDetails; 
  private String orchestrator; 
private String javaVersion; 

}
