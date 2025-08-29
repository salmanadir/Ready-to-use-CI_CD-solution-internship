// src/main/java/com/example/demo/service/DockerfileHistoryService.java
package com.example.demo.service;

import com.example.demo.dto.ContainerPlan;
import com.example.demo.model.DockerfileHistory;
import com.example.demo.model.Repo;
import com.example.demo.repository.DockerfileHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DockerfileHistoryService {

  private final DockerfileHistoryRepository repo;

  // SINGLE
  public DockerfileHistory recordSingle(Repo r, ContainerPlan plan, GitHubService.PushResult pr) {
    String content = plan.getGeneratedDockerfileContent()!=null
        ? plan.getGeneratedDockerfileContent()
        : plan.getExistingDockerfileContent();

    var src = plan.isShouldGenerateDockerfile()
        ? DockerfileHistory.Source.GENERATED
        : DockerfileHistory.Source.EXISTING;

    var entity = DockerfileHistory.builder()
        .repo(r)
        .serviceId(null)
        .workingDirectory(plan.getWorkingDirectory())
        .dockerfilePath(plan.getDockerfilePath())
        .content(content)
        .source(src)
        .commitHash(pr!=null ? pr.getCommitHash() : null)
        .build();

    return repo.save(entity);
  }

  // MULTI
  public DockerfileHistory recordMulti(Repo r,
                                       ContainerizationService.ServiceContainerPlan p,
                                       GitHubService.PushResult pr) {
    String content = p.getGeneratedDockerfileContent()!=null
        ? p.getGeneratedDockerfileContent()
        : p.getExistingDockerfileContent();

    var src = p.isShouldGenerateDockerfile()
        ? DockerfileHistory.Source.GENERATED
        : DockerfileHistory.Source.EXISTING;

    var entity = DockerfileHistory.builder()
        .repo(r)
        .serviceId(p.getServiceId())
        .workingDirectory(p.getWorkingDirectory())
        .dockerfilePath(p.getDockerfilePath())
        .content(content)
        .source(src)
        .commitHash(pr!=null ? pr.getCommitHash() : null)
        .build();

    return repo.save(entity);
  }
}
