package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import com.example.demo.model.CdWorkflow;
import com.example.demo.model.CiWorkflow;
import com.example.demo.model.DeploymentArchitecture;

import java.time.LocalDateTime;
import java.util.List;


@Repository
public interface CdWorkflowRepository extends JpaRepository<CdWorkflow, Long> {


    List<CdWorkflow> findByCiWorkflow_Repo_User_Id(Long userId);


    // Recherche par CI workflow
    List<CdWorkflow> findByCiWorkflow(CiWorkflow ciWorkflow);

    // Recherche par ID de CI workflow
    List<CdWorkflow> findByCiWorkflowCiWorkflowId(Long ciWorkflowId);

    // Recherche par architecture de déploiement
    List<CdWorkflow> findByDeploymentArchitecture(DeploymentArchitecture deploymentArchitecture);

    // Recherche par status
    List<CdWorkflow> findByStatus(CdWorkflow.CdWorkflowStatus status);

    // Recherche par GitHub commit hash
    List<CdWorkflow> findByGithubCommitHash(String githubCommitHash);

    // Recherche par CI workflow et status
    List<CdWorkflow> findByCiWorkflowAndStatus(CiWorkflow ciWorkflow, CdWorkflow.CdWorkflowStatus status);

    // Trouver les workflows créés après une date
    List<CdWorkflow> findByCreatedAtAfter(LocalDateTime date);

    // Trouver les workflows entre deux dates
    List<CdWorkflow> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    
}