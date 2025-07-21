package com.example.demo.model;  
  
import java.time.LocalDateTime;
import java.util.ArrayList;  
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;  
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
  
@Entity  
@Table(name = "template_cd")  
public class TemplateCd {  
    @Id  
    @GeneratedValue(strategy = GenerationType.IDENTITY)  
    private Long id;  
      
    @ManyToOne(fetch = FetchType.LAZY)  
    @JoinColumn(name = "deployment_architecture_id", nullable = false)  
    private DeploymentArchitecture deploymentArchitecture;  
      
    @Lob  
    @Column(columnDefinition = "TEXT")  
    private String cdContent; 
      
    private String description;  
      
    @CreationTimestamp  
    private LocalDateTime createdAt;  
    @JsonIgnore
    @OneToMany(mappedBy = "templateCd", cascade = CascadeType.ALL, fetch = FetchType.LAZY)  
    private List<GeneratedFile> generatedFiles = new ArrayList<>();  
      
    
    public TemplateCd() {}  
      
    public TemplateCd(DeploymentArchitecture deploymentArchitecture, String cdContent, String description) {  
        this.deploymentArchitecture = deploymentArchitecture;  
        this.cdContent = cdContent;  
        this.description = description;  
    }  
      
   
    public Long getId() { return id; }  
    public void setId(Long id) { this.id = id; }  
      
    public DeploymentArchitecture getDeploymentArchitecture() { return deploymentArchitecture; }  
    public void setDeploymentArchitecture(DeploymentArchitecture deploymentArchitecture) { this.deploymentArchitecture = deploymentArchitecture; }  
      
    public String getCdContent() { return cdContent; }  
    public void setCdContent(String cdContent) { this.cdContent = cdContent; }  
      
    public String getDescription() { return description; }  
    public void setDescription(String description) { this.description = description; }  
      
    public LocalDateTime getCreatedAt() { return createdAt; }  
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }  
      
    public List<GeneratedFile> getGeneratedFiles() { return generatedFiles; }  
    public void setGeneratedFiles(List<GeneratedFile> generatedFiles) { this.generatedFiles = generatedFiles; }  
}