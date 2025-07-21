package com.example.demo.model;  
  
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;  

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
  
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
  
@Entity  
@Table(name = "template_ci")  
public class TemplateCi {  
    @Id  
    @GeneratedValue(strategy = GenerationType.IDENTITY)  
    private Long id;  
      
    @Column(nullable = false)  
    private String name;  
      
    @Column(nullable = false)  
    private String stackType; 
      
    @Lob  
    @Column(columnDefinition = "TEXT")  
    private String templateContent; 
    private String description;  
      
    @CreationTimestamp  
    private LocalDateTime createdAt;  
      
    @UpdateTimestamp  
    private LocalDateTime updatedAt;  
    @JsonIgnore 
    @OneToMany(mappedBy = "templateCi", cascade = CascadeType.ALL, fetch = FetchType.LAZY)  
    private List<GeneratedFile> generatedFiles = new ArrayList<>();  
      
   
    public TemplateCi() {}  
      
    public TemplateCi(String name, String stackType, String templateContent, String description) {  
        this.name = name;  
        this.stackType = stackType;  
        this.templateContent = templateContent;  
        this.description = description;  
    }  
      
  
    public Long getId() { return id; }  
    public void setId(Long id) { this.id = id; }  
      
    public String getName() { return name; }  
    public void setName(String name) { this.name = name; }  
      
    public String getStackType() { return stackType; }  
    public void setStackType(String stackType) { this.stackType = stackType; }  
      
    public String getTemplateContent() { return templateContent; }  
    public void setTemplateContent(String templateContent) { this.templateContent = templateContent; }  
      
    public String getDescription() { return description; }  
    public void setDescription(String description) { this.description = description; }  
      
    public LocalDateTime getCreatedAt() { return createdAt; }  
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }  
      
    public LocalDateTime getUpdatedAt() { return updatedAt; }  
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }  
      
    public List<GeneratedFile> getGeneratedFiles() { return generatedFiles; }  
    public void setGeneratedFiles(List<GeneratedFile> generatedFiles) { this.generatedFiles = generatedFiles; }  
}