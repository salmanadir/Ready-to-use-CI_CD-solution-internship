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
  
    Optional<Repo> findByGithubRepoId(String githubRepoId);  
    List<Repo> findByFullName(String fullName);  
    Optional<Repo> findByFullNameAndUser(String fullName, User user);  
    List<Repo> findByUser(User user);  
  
    @Query("SELECT r FROM Repo r WHERE r.user.id = :userId")  
    List<Repo> findByUserId(@Param("userId") Long userId);  
  
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Repo r WHERE r.githubRepoId = :githubRepoId AND r.user.id = :userId")  
    boolean existsByGithubRepoIdAndUserId(@Param("githubRepoId") String githubRepoId, @Param("userId") Long userId);  
  
    @Query("SELECT r FROM Repo r LEFT JOIN FETCH r.ciWorkflows WHERE r.repoId = :repoId")  
    Optional<Repo> findByIdWithCiWorkflows(@Param("repoId") Long repoId);  
  
    long countByUser(User user);  
}