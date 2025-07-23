package com.example.demo.model;

import jakarta.persistence.*;



import java.time.LocalDateTime;

@Entity
@Table(name = "cd_workflows")


public class CdWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cd_workflow_id")
    private Long cdWorkflowId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "github_commit_hash", length = 255)
    private String githubCommitHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CdWorkflowStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Relation Many-to-One avec CiWorkflow
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ci_workflow_id", nullable = false)
    private CiWorkflow ciWorkflow;

    // Relation Many-to-One avec DeploymentArchitecture
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_architecture_id", nullable = false)
    private DeploymentArchitecture deploymentArchitecture;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum CdWorkflowStatus {
        PENDING, DEPLOYED, FAILED, ROLLBACK
    }

    public Long getCdWorkflowId() {
        return cdWorkflowId;
    }

    public void setCdWorkflowId(Long cdWorkflowId) {
        this.cdWorkflowId = cdWorkflowId;
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

    public com.example.demo.model.CdWorkflow.CdWorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(com.example.demo.model.CdWorkflow.CdWorkflowStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public CiWorkflow getCiWorkflow() {
        return ciWorkflow;
    }

    public void setCiWorkflow(CiWorkflow ciWorkflow) {
        this.ciWorkflow = ciWorkflow;
    }

    public DeploymentArchitecture getDeploymentArchitecture() {
        return deploymentArchitecture;
    }

    public void setDeploymentArchitecture(DeploymentArchitecture deploymentArchitecture) {
        this.deploymentArchitecture = deploymentArchitecture;
    }
}