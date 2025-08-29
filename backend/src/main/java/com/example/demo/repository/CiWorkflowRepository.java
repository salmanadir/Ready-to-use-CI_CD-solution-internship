package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import com.example.demo.model.CiWorkflow;
import com.example.demo.model.Repo;

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



  
}