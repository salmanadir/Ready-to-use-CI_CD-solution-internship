package com.example.demo.model;

import jakarta.persistence.*;



import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ci_workflows")


public class CiWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ci_workflow_id")
    private Long ciWorkflowId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "github_commit_hash", length = 255)
    private String githubCommitHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkflowStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Relation Many-to-One avec Repo
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repo repo;

    // Relation One-to-Many avec CdWorkflow
    @OneToMany(mappedBy = "ciWorkflow", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<CdWorkflow> cdWorkflows;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum WorkflowStatus {
        PENDING, COMMITTED, FAILED, RUNNING
    }

    public Long getCiWorkflowId() {
        return ciWorkflowId;
    }

    public void setCiWorkflowId(Long ciWorkflowId) {
        this.ciWorkflowId = ciWorkflowId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getGithubCommitHash() {
        return githubCommitHash;
    }

    public void setGithubCommitHash(String githubCommitHash) {
        this.githubCommitHash = githubCommitHash;
    }

    public com.example.demo.model.CiWorkflow.WorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(com.example.demo.model.CiWorkflow.WorkflowStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Repo getRepo() {
        return repo;
    }

    public void setRepo(Repo repo) {
        this.repo = repo;
    }

    public List<CdWorkflow> getCdWorkflows() {
        return cdWorkflows;
    }

    public void setCdWorkflows(List<CdWorkflow> cdWorkflows) {
        this.cdWorkflows = cdWorkflows;
    }
}