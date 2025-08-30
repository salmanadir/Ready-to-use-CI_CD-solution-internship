package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Compter par status
    long countByStatus(CdWorkflow.CdWorkflowStatus status);

    // Compter par architecture de déploiement
    long countByDeploymentArchitecture(DeploymentArchitecture deploymentArchitecture);

    // Trouver les derniers déploiements réussis
    @Query("SELECT cd FROM CdWorkflow cd WHERE cd.status = 'DEPLOYED' ORDER BY cd.createdAt DESC")
    List<CdWorkflow> findLatestSuccessfulDeployments();

    // Trouver les déploiements par repo (via CI workflow)
    @Query("SELECT cd FROM CdWorkflow cd JOIN cd.ciWorkflow ci WHERE ci.repo.repoId = :repoId ORDER BY cd.createdAt DESC")
    List<CdWorkflow> findByRepoId(@Param("repoId") Long repoId);

    // Trouver le dernier déploiement pour un CI workflow
    @Query("SELECT cd FROM CdWorkflow cd WHERE cd.ciWorkflow = :ciWorkflow ORDER BY cd.createdAt DESC")
    List<CdWorkflow> findLatestByCiWorkflow(@Param("ciWorkflow") CiWorkflow ciWorkflow);
}