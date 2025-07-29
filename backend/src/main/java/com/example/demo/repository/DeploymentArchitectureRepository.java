package com.example.demo.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

import com.example.demo.model.DeploymentArchitecture;

import java.util.Optional;

@Repository
public interface DeploymentArchitectureRepository extends JpaRepository<DeploymentArchitecture, Long> {

    // Recherche par clé de template CD
    Optional<DeploymentArchitecture> findByCdTemplateKey(String cdTemplateKey);

  

   


}