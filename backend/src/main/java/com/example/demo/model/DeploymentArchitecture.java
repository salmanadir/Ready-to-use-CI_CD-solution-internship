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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
  
@Entity  
@Table(name = "deployment_architecture")  
public class DeploymentArchitecture {  
    @Id  
    @GeneratedValue(strategy = GenerationType.IDENTITY)  
    private Long id;  
      
    @Column(nullable = false, unique = true)  
    private String name; 
      
    private String description;  
      
    @CreationTimestamp  
    private LocalDateTime createdAt;  
     @JsonIgnore
    @OneToMany(mappedBy = "deploymentArchitecture", cascade = CascadeType.ALL, fetch = FetchType.LAZY)  
    private List<TemplateCd> templateCds = new ArrayList<>();  
      
    
    public DeploymentArchitecture() {}  
      
    public DeploymentArchitecture(String name, String description) {  
        this.name = name;  
        this.description = description;  
    }  
      
    
    public Long getId() { return id; }  
    public void setId(Long id) { this.id = id; }  
      
    public String getName() { return name; }  
    public void setName(String name) { this.name = name; }  
      
    public String getDescription() { return description; }  
    public void setDescription(String description) { this.description = description; }  
      
    public LocalDateTime getCreatedAt() { return createdAt; }  
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }  
      
    public List<TemplateCd> getTemplateCds() { return templateCds; }  
    public void setTemplateCds(List<TemplateCd> templateCds) { this.templateCds = templateCds; }  
}