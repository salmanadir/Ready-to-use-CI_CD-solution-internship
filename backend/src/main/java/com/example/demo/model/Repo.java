package com.example.demo.model;  
  

import java.util.ArrayList;
import java.util.List;

  
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
  
@Entity  
@Table(name = "repo")  
public class Repo {  
    @Id  
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="repo_id")
    private Long repoId;
      
    @ManyToOne(fetch = FetchType.EAGER)  
    @JoinColumn(name = "user_id", nullable = false)  
    @JsonIgnore 
    private User user;  
      
    @Column(name = "github_repo_id", nullable = false, unique = true, length = 255)
    private String githubRepoId;


    @Column(name = "full_name", length = 500)
    private String fullName;

    @Column(name = "technical_details", columnDefinition = "TEXT")
    private String technicalDetails;

    @Column(name = "url", length = 500)
    private String url;
    private String defaultBranch;  

    @OneToMany(mappedBy = "repo", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonIgnore 
    private List<CiWorkflow> ciWorkflows = new ArrayList<>();
    


    public Long getRepoId() {
        return repoId;
    }

    public void setRepoId(Long repoId) {
        this.repoId = repoId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getGithubRepoId() {
        return githubRepoId;
    }

    public void setGithubRepoId(String githubRepoId) {
        this.githubRepoId = githubRepoId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getTechnicalDetails() {
        return technicalDetails;
    }

    public void setTechnicalDetails(String technicalDetails) {
        this.technicalDetails = technicalDetails;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<CiWorkflow> getCiWorkflows() {
        return ciWorkflows;
    }

    public void setCiWorkflows(List<CiWorkflow> ciWorkflows) {
        this.ciWorkflows = ciWorkflows;
    }

}