// src/main/java/com/example/demo/repository/DockerfileHistoryRepository.java
package com.example.demo.repository;

import com.example.demo.model.DockerfileHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DockerfileHistoryRepository extends JpaRepository<DockerfileHistory, Long> {}
