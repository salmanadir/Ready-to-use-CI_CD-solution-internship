package com.example.demo.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.model.DeploymentArchitecture;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentArchitectureRepository extends JpaRepository<DeploymentArchitecture, Long> {

    // Recherche par clé de template CD
    Optional<DeploymentArchitecture> findByCdTemplateKey(String cdTemplateKey);

    // Recherche par nom
    Optional<DeploymentArchitecture> findByName(String name);

    // Vérifier si une architecture existe par clé de template
    boolean existsByCdTemplateKey(String cdTemplateKey);

    // Vérifier si une architecture existe par nom
    boolean existsByName(String name);

    // Recherche par nom contenant (recherche partielle)
    List<DeploymentArchitecture> findByNameContainingIgnoreCase(String name);

    // Requête personnalisée pour trouver une architecture avec ses CD workflows
    @Query("SELECT d FROM DeploymentArchitecture d LEFT JOIN FETCH d.cdWorkflows WHERE d.deploymentArchitectureId = :deploymentArchitectureId")
    Optional<DeploymentArchitecture> findByIdWithCdWorkflows(@Param("deploymentArchitectureId") Long deploymentArchitectureId);

    // Compter le nombre d'utilisations d'une architecture
    @Query("SELECT COUNT(cd) FROM CdWorkflow cd WHERE cd.deploymentArchitecture = :architecture")
    long countUsageByArchitecture(@Param("architecture") DeploymentArchitecture architecture);
}