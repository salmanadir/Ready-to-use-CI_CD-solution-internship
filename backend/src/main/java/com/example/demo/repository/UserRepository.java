package com.example.demo.repository;  
  
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.model.User;  
  
@Repository  
public interface UserRepository extends JpaRepository<User, Long> {  
  
    // Recherche par GitHub ID (Long pour compatibilité avec le nouveau modèle User)  
    Optional<User> findByGithubId(Long githubId);  
  
    // Recherche par username  
    Optional<User> findByUsername(String username);  
  
    // Recherche par email  
    Optional<User> findByEmail(String email);  
  
    // Vérifier si un utilisateur existe par GitHub ID  
    boolean existsByGithubId(Long githubId);  
  
    // Vérifier si un utilisateur existe par username  
    boolean existsByUsername(String username);  
}