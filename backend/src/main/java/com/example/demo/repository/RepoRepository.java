package com.example.demo.repository;  
  
import com.example.demo.model.Repo;  
import com.example.demo.model.User;  
import org.springframework.data.jpa.repository.JpaRepository;  
import org.springframework.stereotype.Repository;  
import java.util.List;  
import java.util.Optional;  
  
@Repository  
public interface RepoRepository extends JpaRepository<Repo, Long> {  
      
    List<Repo> findByUser(User user);  
      
    List<Repo> findByUserId(Long userId);  
      
    Optional<Repo> findByGithubRepoId(String githubRepoId);  
      
    boolean existsByGithubRepoId(String githubRepoId);  
      
    List<Repo> findByUserAndNameContaining(User user, String name);  
}