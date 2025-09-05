package com.example.demo.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true) 
public class StackAnalysis {


    private String mode;
    private String stackType;
    private String javaVersion;
    private String orchestrator;         
    private String workingDirectory;
    private List<Map<String, Object>> files;

    private String buildTool;
    private String language;
    private Map<String, Object> projectDetails;

   
    private String databaseType; 
    private String databaseName; 

        // Pour mode "multi"  
    private List<ServiceAnalysis> services;  
      
    // Pour mode "single"   
    private ServiceAnalysis analysis;  
   
    public StackAnalysis(String stackType,
                         String javaVersion,
                         String orchestrator,
                         String workingDirectory,
                         List<Map<String, Object>> files) {
        this.stackType = stackType;
        this.javaVersion = javaVersion;
        this.orchestrator = orchestrator;
        this.workingDirectory = workingDirectory;
        this.files = files;
    }

    public StackAnalysis() {}

  @SuppressWarnings("unchecked")
public static StackAnalysis fromMap(Map<String,Object> svc) {
    if (svc == null) return null;
    StackAnalysis sa = new StackAnalysis();
    sa.setStackType((String) svc.get("stackType"));
    sa.setWorkingDirectory((String) svc.get("workingDirectory"));
    sa.setBuildTool((String) svc.get("buildTool"));
    sa.setLanguage((String) svc.get("language"));
    sa.setJavaVersion((String) svc.get("javaVersion"));
    sa.setOrchestrator((String) svc.get("orchestrator"));
    sa.setProjectDetails((Map<String,Object>) svc.get("projectDetails"));
    return sa;
}

    public String getStackType() { return stackType; }
    public void setStackType(String stackType) { this.stackType = stackType; }

    public String getJavaVersion() { return javaVersion; }
    public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }

    public String getOrchestrator() { return orchestrator; }
    public void setOrchestrator(String orchestrator) { this.orchestrator = orchestrator; }

    public String getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }

    public List<Map<String, Object>> getFiles() { return files; }
    public void setFiles(List<Map<String, Object>> files) { this.files = files; }

    public String getBuildTool() { return buildTool; }
    public void setBuildTool(String buildTool) { this.buildTool = buildTool; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Map<String, Object> getProjectDetails() { return projectDetails; }
    public void setProjectDetails(Map<String, Object> projectDetails) { this.projectDetails = projectDetails; }

    public String getDatabaseType() { return databaseType; }
    public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }

    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

    public String getMode() { return mode; }  
    public void setMode(String mode) { this.mode = mode; }  

    public List<ServiceAnalysis> getServices() { return services; }  
    public void setServices(List<ServiceAnalysis> services) { this.services = services; }  
      
    public ServiceAnalysis getAnalysis() { return analysis; }  
    public void setAnalysis(ServiceAnalysis analysis) { this.analysis = analysis; }
}