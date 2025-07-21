package com.example.demo.model;  
  
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
  
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
  
@Entity  
@Table(name = "repo")  
public class Repo {  
    @Id  
    @GeneratedValue(strategy = GenerationType.IDENTITY)  
    private Long id;  
      
    @ManyToOne(fetch = FetchType.LAZY)  
    @JoinColumn(name = "user_id", nullable = false)  
    @JsonIgnore 
    private User user;  
      
    @Column(unique = true, nullable = false)  
    private String githubRepoId;  
      
    @Column(nullable = false)  
    private String name;  
      
    private String fullName;  
    private String description;  
    private String url;  
    private String defaultBranch;  
      
    @Column(columnDefinition = "TEXT")  
    private String technicalDetails;  
      
    @CreationTimestamp  
    private LocalDateTime createdAt;  
      
   
    public Repo() {}  
      
    
    public Long getId() { return id; }  
    public void setId(Long id) { this.id = id; }  
      
    public User getUser() { return user; }  
    public void setUser(User user) { this.user = user; }  
      
    public String getGithubRepoId() { return githubRepoId; }  
    public void setGithubRepoId(String githubRepoId) { this.githubRepoId = githubRepoId; }  
      
    public String getName() { return name; }  
    public void setName(String name) { this.name = name; }  
      
    public String getFullName() { return fullName; }  
    public void setFullName(String fullName) { this.fullName = fullName; }  
      
    public String getDescription() { return description; }  
    public void setDescription(String description) { this.description = description; }  
      
    public String getUrl() { return url; }  
    public void setUrl(String url) { this.url = url; }  
      
    public String getDefaultBranch() { return defaultBranch; }  
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }  
      
    public String getTechnicalDetails() { return technicalDetails; }  
    public void setTechnicalDetails(String technicalDetails) { this.technicalDetails = technicalDetails; }  
      
    public LocalDateTime getCreatedAt() { return createdAt; }  
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }  
}