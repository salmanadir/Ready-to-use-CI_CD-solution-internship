// src/main/java/com/example/demo/repository/DockerComposeHistoryRepository.java
package com.example.demo.repository;

import com.example.demo.model.DockerComposeHistory;
import com.example.demo.model.Repo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DockerComposeHistoryRepository extends JpaRepository<DockerComposeHistory, Long> {
  List<DockerComposeHistory> findByRepoOrderByCreatedAtDesc(Repo repo);
  List<DockerComposeHistory> findByRepoAndComposePathOrderByCreatedAtDesc(Repo repo, String composePath);
}
