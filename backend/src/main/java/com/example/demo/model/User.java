package com.example.demo.model;  
  

import java.util.ArrayList;
import java.util.List;


  
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
    @Column (name="user_id")
    private Long userId;
  
    @Column(unique = true, nullable = false, name="github_id", length= 255)
    private String githubId;    
  
    @Column(nullable = false, name="username", length = 255)    
    private String username;    
  
    @Column(name="token", length = 500)    
    private String token;    
    
    @Column(name= "email", length = 255)
    private String email;
  
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)  
    @JsonIgnore  
    private List<Repo> repositories = new ArrayList<>();


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long id) { this.userId = id; }  
      
    public String getGithubId() { return githubId; }  
    public void setGithubId(String githubId) { this.githubId = githubId; }  
      
    public String getUsername() { return username; }  
    public void setUsername(String username) { this.username = username; }  
      
    public String getToken() { return token; }  
    public void setToken(String token) { this.token = token; }  

      
    public List<Repo> getRepositories() { return repositories; }  
    public void setRepositories(List<Repo> repositories) { this.repositories = repositories; }  
}