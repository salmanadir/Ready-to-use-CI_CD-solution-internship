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
@Table(name = "users")  
public class User {  
    @Id    
    @GeneratedValue(strategy = GenerationType.IDENTITY)  
    private Long id;  
  
    @Column(unique = true, nullable = false)    
    private String githubId;    
  
    @Column(nullable = false)    
    private String username;    
  
    @Column(columnDefinition = "TEXT")    
    private String token;    
  
    @CreationTimestamp    
    private LocalDateTime createdAt;    
  
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)  
    @JsonIgnore  
    private List<Repo> repositories = new ArrayList<>();    
  
   
    public User() {}    
  

    public Long getId() { return id; }  
    public void setId(Long id) { this.id = id; }  
      
    public String getGithubId() { return githubId; }  
    public void setGithubId(String githubId) { this.githubId = githubId; }  
      
    public String getUsername() { return username; }  
    public void setUsername(String username) { this.username = username; }  
      
    public String getToken() { return token; }  
    public void setToken(String token) { this.token = token; }  
      
    public LocalDateTime getCreatedAt() { return createdAt; }  
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }  
      
    public List<Repo> getRepositories() { return repositories; }  
    public void setRepositories(List<Repo> repositories) { this.repositories = repositories; }  
}