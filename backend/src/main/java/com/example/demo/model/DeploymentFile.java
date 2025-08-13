package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "deployment_files")
public class DeploymentFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deployment_file_id")
    private Long deploymentFileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_request_id", nullable = false)
    private DeploymentRequest deploymentRequest;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 50)
    private String fileType; // e.g., DOCKERFILE, COMPOSE

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Getters and setters
    public Long getDeploymentFileId() { return deploymentFileId; }
    public void setDeploymentFileId(Long deploymentFileId) { this.deploymentFileId = deploymentFileId; }
    public DeploymentRequest getDeploymentRequest() { return deploymentRequest; }
    public void setDeploymentRequest(DeploymentRequest deploymentRequest) { this.deploymentRequest = deploymentRequest; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
