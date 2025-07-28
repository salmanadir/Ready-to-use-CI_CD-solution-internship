package com.example.demo.repository;  
  
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;  
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.model.DeploymentArchitecture;  
  
@Repository  
public interface DeploymentArchitectureRepository extends JpaRepository<DeploymentArchitecture, Long> {  
  
  
    Optional<DeploymentArchitecture> findByCdTemplateKey(String cdTemplateKey);  

    Optional<DeploymentArchitecture> findByNameArchitecture(String nameArchitecture);  
  
    boolean existsByCdTemplateKey(String cdTemplateKey);  
  

    boolean existsByNameArchitecture(String nameArchitecture);  
  
   
    List<DeploymentArchitecture> findByNameArchitectureContainingIgnoreCase(String nameArchitecture);  

    @Query("SELECT d FROM DeploymentArchitecture d LEFT JOIN FETCH d.cdWorkflows WHERE d.deploymentArchitectureId = :deploymentArchitectureId")  
    Optional<DeploymentArchitecture> findByIdWithCdWorkflows(@Param("deploymentArchitectureId") Long deploymentArchitectureId);  
  
    
    @Query("SELECT COUNT(cd) FROM CdWorkflow cd WHERE cd.deploymentArchitecture = :architecture")  
    long countUsageByArchitecture(@Param("architecture") DeploymentArchitecture architecture);  
}