package com.example.demo.repository;  
  
import com.example.demo.model.TemplateCi;  
import org.springframework.data.jpa.repository.JpaRepository;  
import org.springframework.stereotype.Repository;  
import java.util.List;  
import java.util.Optional;  
  
@Repository  
public interface TemplateCiRepository extends JpaRepository<TemplateCi, Long> {  
      
    List<TemplateCi> findByStackType(String stackType);  
      
    Optional<TemplateCi> findByNameAndStackType(String name, String stackType);  
      
    boolean existsByNameAndStackType(String name, String stackType);  
}