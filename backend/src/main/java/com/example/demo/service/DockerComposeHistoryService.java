// src/main/java/com/example/demo/service/DockerComposeHistoryService.java
package com.example.demo.service;

import com.example.demo.model.DockerComposeHistory;
import com.example.demo.model.Repo;
import com.example.demo.repository.DockerComposeHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class DockerComposeHistoryService {
  public java.util.List<DockerComposeHistory> getHistoriesByRepo(Repo repoEntity) {
    return repo.findByRepoOrderByCreatedAtDesc(repoEntity);
  }

  private final DockerComposeHistoryRepository repo;

  /** Enregistre un compose EXISTANT détecté (aucun push effectué). */
  public DockerComposeHistory recordExisting(Repo r, String composePath, String content,
                                             DockerComposeHistory.Mode mode,
                                             Collection<String> serviceNames) {
    return repo.save(
      DockerComposeHistory.builder()
        .repo(r)
        .composePath(composePath)
        .content(content)
        .source(DockerComposeHistory.Source.EXISTING)
        .mode(mode != null ? mode : DockerComposeHistory.Mode.SINGLE)
        .serviceNames(serviceNames != null ? String.join(",", serviceNames) : null)
        .commitHash(null)
        .build()
    );
  }

  /** Enregistre un compose GÉNÉRÉ (push effectué ou pas). */
  public DockerComposeHistory recordGenerated(Repo r, String composePath, String content,
                                              DockerComposeHistory.Mode mode,
                                              Collection<String> serviceNames,
                                              GitHubService.PushResult pr) {
    return repo.save(
      DockerComposeHistory.builder()
        .repo(r)
        .composePath(composePath)
        .content(content)
        .source(DockerComposeHistory.Source.GENERATED)
        .mode(mode != null ? mode : DockerComposeHistory.Mode.SINGLE)
        .serviceNames(serviceNames != null ? String.join(",", serviceNames) : null)
        .commitHash(pr != null ? pr.getCommitHash() : null)
        .build()
    );
  }

  /** Enregistre un compose MIS À JOUR (par ex. régénéré et repushé). */
  public DockerComposeHistory recordUpdated(Repo r, String composePath, String content,
                                            DockerComposeHistory.Mode mode,
                                            Collection<String> serviceNames,
                                            GitHubService.PushResult pr) {
    return repo.save(
      DockerComposeHistory.builder()
        .repo(r)
        .composePath(composePath)
        .content(content)
        .source(DockerComposeHistory.Source.UPDATED)
        .mode(mode != null ? mode : DockerComposeHistory.Mode.SINGLE)
        .serviceNames(serviceNames != null ? String.join(",", serviceNames) : null)
        .commitHash(pr != null ? pr.getCommitHash() : null)
        .build()
    );
  }
}
