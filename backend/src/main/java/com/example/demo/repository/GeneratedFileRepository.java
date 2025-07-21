package com.example.demo.repository;  
  
import java.util.List;  
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.model.GeneratedFile;
import com.example.demo.model.Repo;
import com.example.demo.model.User;  
  
@Repository  
public interface GeneratedFileRepository extends JpaRepository<GeneratedFile, Long> {  
      
 
    List<GeneratedFile> findByUser(User user);  
      
    List<GeneratedFile> findByUserId(Long userId);  
 
    List<GeneratedFile> findByRepo(Repo repo);  
      
    List<GeneratedFile> findByRepoId(Long repoId);  
      
  
    List<GeneratedFile> findByUserAndRepo(User user, Repo repo);  
      
    List<GeneratedFile> findByUserIdAndRepoId(Long userId, Long repoId);  
      
 
    List<GeneratedFile> findByStatus(String status);  
      
    List<GeneratedFile> findByUserAndStatus(User user, String status);  
      

    List<GeneratedFile> findByFileName(String fileName);  
      
    Optional<GeneratedFile> findByUserAndRepoAndFileName(User user, Repo repo, String fileName);  
      

    Optional<GeneratedFile> findByGithubCommitHash(String githubCommitHash);  
      
    @Query("SELECT gf FROM GeneratedFile gf WHERE gf.user.id = :userId ORDER BY gf.createdAt DESC")  
    List<GeneratedFile> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);  
      
    @Query("SELECT gf FROM GeneratedFile gf WHERE gf.repo.id = :repoId ORDER BY gf.createdAt DESC")  
    List<GeneratedFile> findByRepoIdOrderByCreatedAtDesc(@Param("repoId") Long repoId);  
      
 
    long countByUser(User user);  
      
    long countByUserId(Long userId);  
      
   
    long countByStatus(String status);  
      
    long countByUserAndStatus(User user, String status);  
      
   
    boolean existsByUserAndRepoAndFileName(User user, Repo repo, String fileName);  
      
    boolean existsByGithubCommitHash(String githubCommitHash);  
}