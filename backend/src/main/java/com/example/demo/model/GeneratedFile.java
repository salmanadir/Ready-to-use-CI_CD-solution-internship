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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity  
@Table(name = "generated_files")  
public class GeneratedFile {  
    @Id  
    @GeneratedValue(strategy = GenerationType.IDENTITY)  
    private Long id;  
      
    @ManyToOne(fetch = FetchType.LAZY)  
    @JoinColumn(name = "user_id", nullable = false)  
    @JsonIgnore 
    private User user;  
      
    @ManyToOne(fetch = FetchType.LAZY)  
    @JoinColumn(name = "repo_id", nullable = false)  
    @JsonIgnore  
    private Repo repo;  
      
    @ManyToOne(fetch = FetchType.LAZY)  
    @JoinColumn(name = "template_ci_id", nullable = false)  
    @JsonIgnore  
    private TemplateCi templateCi;  
      
    @ManyToOne(fetch = FetchType.LAZY)  
    @JoinColumn(name = "template_cd_id", nullable = false)  
    @JsonIgnore  
    private TemplateCd templateCd;  
      
    @Column(nullable = false)  
    private String fileName;  
      
    private String filePath;  
      
    @Lob  
    @Column(columnDefinition = "TEXT")  
    private String mergedContent;  
      
    @Column(nullable = false)  
    private String status;  
      
    private String githubCommitHash;  
      
    @CreationTimestamp  
    private LocalDateTime createdAt;  
      
   
    public GeneratedFile() {}  

    public Long getId() { return id; }  
    public void setId(Long id) { this.id = id; }  
      
    public User getUser() { return user; }  
    public void setUser(User user) { this.user = user; }  
      
    public Repo getRepo() { return repo; }  
    public void setRepo(Repo repo) { this.repo = repo; }  
      
    public TemplateCi getTemplateCi() { return templateCi; }  
    public void setTemplateCi(TemplateCi templateCi) { this.templateCi = templateCi; }  
      
    public TemplateCd getTemplateCd() { return templateCd; }  
    public void setTemplateCd(TemplateCd templateCd) { this.templateCd = templateCd; }  
      
    public String getFileName() { return fileName; }  
    public void setFileName(String fileName) { this.fileName = fileName; }  
      
    public String getFilePath() { return filePath; }  
    public void setFilePath(String filePath) { this.filePath = filePath; }  
      
    public String getMergedContent() { return mergedContent; }  
    public void setMergedContent(String mergedContent) { this.mergedContent = mergedContent; }  
      
    public String getStatus() { return status; }  
    public void setStatus(String status) { this.status = status; }  
      
    public String getGithubCommitHash() { return githubCommitHash; }  
    public void setGithubCommitHash(String githubCommitHash) { this.githubCommitHash = githubCommitHash; }  
      
    public LocalDateTime getCreatedAt() { return createdAt; }  
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }  
}