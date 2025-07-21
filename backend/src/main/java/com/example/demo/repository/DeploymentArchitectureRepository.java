package com.example.demo.repository;  
  
import com.example.demo.model.DeploymentArchitecture;  
import org.springframework.data.jpa.repository.JpaRepository;  
import org.springframework.stereotype.Repository;  
import java.util.Optional;  
  
@Repository  
public interface DeploymentArchitectureRepository extends JpaRepository<DeploymentArchitecture, Long> {  
      
    Optional<DeploymentArchitecture> findByName(String name);  
      
    boolean existsByName(String name);  
}