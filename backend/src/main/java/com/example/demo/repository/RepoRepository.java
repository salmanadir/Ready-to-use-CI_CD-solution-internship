package com.example.demo.repository;    
    
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.model.Repo;  
import com.example.demo.model.User;    
    
@Repository    
public interface RepoRepository extends JpaRepository<Repo, Long> {    
    
    // Recherche par GitHub Repository ID  
    Optional<Repo> findByGithubRepoId(String githubRepoId);  
  
    // Recherche par nom  
    List<Repo> findByFullName(String fullName);  
  
    // Recherche par propriétaire  
    List<Repo> findByUser(User user);  
  
    // Recherche par nom et propriétaire  
    Optional<Repo> findByFullNameAndUser(String fullName, User user);  
  
    // Recherche par ID de propriétaire (votre méthode personnalisée)  
    @Query("SELECT r FROM Repo r WHERE r.user.id = :userId")    
    List<Repo> findByUserId(@Param("userId") Long userId);  
  
    // Vérifier si un repo existe par GitHub Repository ID et utilisateur  
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Repo r WHERE r.githubRepoId = :githubRepoId AND r.user.id = :userId")    
    boolean existsByGithubRepoIdAndUserId(@Param("githubRepoId") String githubRepoId, @Param("userId") Long userId);  
  
    // Requête personnalisée pour trouver un repo avec ses workflows CI  
    @Query("SELECT r FROM Repo r LEFT JOIN FETCH r.ciWorkflows WHERE r.repoId = :repoId")  
    Optional<Repo> findByIdWithCiWorkflows(@Param("repoId") Long repoId);  
  
    // Compter le nombre de repos par utilisateur  
    long countByUser(User user);  
}