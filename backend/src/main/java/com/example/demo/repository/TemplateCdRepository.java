package com.example.demo.repository;  
  
import com.example.demo.model.TemplateCd;  
import com.example.demo.model.DeploymentArchitecture;  
import org.springframework.data.jpa.repository.JpaRepository;  
import org.springframework.stereotype.Repository;  
import java.util.List;  
import java.util.Optional;  
  
@Repository  
public interface TemplateCdRepository extends JpaRepository<TemplateCd, Long> {  
      
    List<TemplateCd> findByDeploymentArchitecture(DeploymentArchitecture deploymentArchitecture);  
      
    List<TemplateCd> findByDeploymentArchitectureId(Long deploymentArchitectureId);  
      
    Optional<TemplateCd> findByDeploymentArchitectureAndDescription(DeploymentArchitecture deploymentArchitecture, String description);  
}