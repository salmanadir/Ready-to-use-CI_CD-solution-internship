package com.example.demo.repository;

import  com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Recherche par username
    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.userId = :userId") // JPQL explicite
    Optional<User> findUserById(@Param("userId") Long userId);
  
    Optional<User> findByGithubId(Long githubId);  
    
    Optional<User> findByEmail(String email);  
  
    boolean existsByGithubId(Long githubId);  
    boolean existsByUsername(String username);  

}