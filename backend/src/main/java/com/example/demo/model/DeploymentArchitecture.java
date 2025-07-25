package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;
@Data
@Entity
@Table(name = "deployment_architectures")

public class DeploymentArchitecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deployment_architecture_id")
    private Long deploymentArchitectureId;

    @Column(name = "name_architecture", nullable = false, length = 255)
    private String nameArchitecture;

    @Column(name = "description_architecture", columnDefinition = "TEXT")
    private String descriptionArchitecture;

    @Column(name = "cd_template_key", nullable = false, unique = true, length = 255)
    private String cdTemplateKey;

    // Relation One-to-Many avec CdWorkflow
    @OneToMany(mappedBy = "deploymentArchitecture", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<CdWorkflow> cdWorkflows;
}