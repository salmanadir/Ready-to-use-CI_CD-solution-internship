package com.example.demo.repository;

import com.example.demo.model.Repo;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepoRepository extends JpaRepository<Repo, Long> {

    // Recherche par GitHub Repository ID
    Optional<Repo> findByGithubRepoId(String githubRepoId);

    // Recherche par nom
    List<Repo> findByName(String name);

    // Recherche par propriétaire
    List<Repo> findByUser(User user);

    // Recherche par ID de propriétaire
    List<Repo> findByUserUserId(Long userId);

    // Vérifier si un repo existe par GitHub Repository ID
    boolean existsByGithubRepoId(String githubRepoId);

    // Recherche par nom et propriétaire
    Optional<Repo> findByNameAndUser(String name, User user);

    // Requête personnalisée pour trouver un repo avec ses workflows CI
    @Query("SELECT r FROM Repo r LEFT JOIN FETCH r.ciWorkflows WHERE r.repoId = :repoId")
    Optional<Repo> findByIdWithCiWorkflows(@Param("repoId") Long repoId);

    // Compter le nombre de repos par utilisateur
    long countByUser(User user);
}