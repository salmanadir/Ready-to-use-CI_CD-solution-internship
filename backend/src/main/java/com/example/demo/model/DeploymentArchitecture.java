package com.example.demo.model;  
  
import jakarta.persistence.*;  
import java.util.List;  
  
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
  
    @OneToMany(mappedBy = "deploymentArchitecture", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)  
    private List<CdWorkflow> cdWorkflows;  
  
    // Getters et setters  
    public Long getDeploymentArchitectureId() { return deploymentArchitectureId; }  
    public void setDeploymentArchitectureId(Long deploymentArchitectureId) { this.deploymentArchitectureId = deploymentArchitectureId; }  
  
    public String getNameArchitecture() { return nameArchitecture; }  
    public void setNameArchitecture(String nameArchitecture) { this.nameArchitecture = nameArchitecture; }  
  
    public String getDescriptionArchitecture() { return descriptionArchitecture; }  
    public void setDescriptionArchitecture(String descriptionArchitecture) { this.descriptionArchitecture = descriptionArchitecture; }  
  
    public String getCdTemplateKey() { return cdTemplateKey; }  
    public void setCdTemplateKey(String cdTemplateKey) { this.cdTemplateKey = cdTemplateKey; }  
  
    public List<CdWorkflow> getCdWorkflows() { return cdWorkflows; }  
    public void setCdWorkflows(List<CdWorkflow> cdWorkflows) { this.cdWorkflows = cdWorkflows; }  
}