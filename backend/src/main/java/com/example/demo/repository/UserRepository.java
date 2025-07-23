
package com.example.demo.repository;

import  com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Recherche par GitHub ID
    Optional<User> findByGithubId(String githubId);

    // Recherche par username
    Optional<User> findByUsername(String username);

    // Recherche par email
    Optional<User> findByEmail(String email);

    // Vérifier si un utilisateur existe par GitHub ID
    boolean existsByGithubId(String githubId);

    // Vérifier si un utilisateur existe par username
    boolean existsByUsername(String username);

}