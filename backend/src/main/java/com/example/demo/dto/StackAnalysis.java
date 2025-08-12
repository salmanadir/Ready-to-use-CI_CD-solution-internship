package com.example.demo.dto;

import java.util.List;
import java.util.Map;

public class StackAnalysis {

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

    // --- Getters/Setters ---
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
}
