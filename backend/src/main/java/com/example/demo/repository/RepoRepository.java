package com.example.demo.repository;

import com.example.demo.model.Repo;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepoRepository extends JpaRepository<Repo, Long> {

    // Recherche par GitHub Repository ID
    Optional<Repo> findByGithubRepoId(String githubRepoId);


    // Recherche par propriétaire
    List<Repo> findByUser(User user);

   
    List<Repo> findByUserUserId(Long userId);
    
    default Repo findByIdOrThrow(Long repoId) {
        return findById(repoId)
            .orElseThrow(() -> new IllegalArgumentException("Repo not found with id: " + repoId));
    }


}