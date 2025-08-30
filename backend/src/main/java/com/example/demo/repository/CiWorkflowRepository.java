package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.repository.CiWorkflowRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query; // Add this import
import org.springframework.data.repository.query.Param; // Add 
import com.example.demo.model.CiWorkflow;
import com.example.demo.model.Repo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CiWorkflowRepository extends JpaRepository<CiWorkflow, Long> {

    // Recherche par repository
    List<CiWorkflow> findByRepo(Repo repo);

    // Recherche par ID de repository
    List<CiWorkflow> findByRepoRepoId(Long repoId);

    // Recherche par status
    List<CiWorkflow> findByStatus(CiWorkflow.WorkflowStatus status);

    // Recherche par GitHub commit hash
    Optional<CiWorkflow> findByGithubCommitHash(String githubCommitHash);


    // Recherche par repository et status
    List<CiWorkflow> findByRepoAndStatus(Repo repo, CiWorkflow.WorkflowStatus status);

    // Trouver les workflows créés après une date
    List<CiWorkflow> findByCreatedAtAfter(LocalDateTime date);

    // Trouver les workflows entre deux dates
    List<CiWorkflow> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<CiWorkflow> findByRepo_User_UserId(Long userId);
    
    // Requête personnalisée pour trouver un workflow avec ses CD workflows
    @Query("SELECT c FROM CiWorkflow c LEFT JOIN FETCH c.cdWorkflows WHERE c.ciWorkflowId = :ciWorkflowId")
    Optional<CiWorkflow> findByIdWithCdWorkflows(@Param("ciWorkflowId") Long ciWorkflowId);


  
}