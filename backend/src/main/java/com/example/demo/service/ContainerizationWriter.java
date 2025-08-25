package com.example.demo.service;

import com.example.demo.dto.ContainerPlan;
import com.example.demo.dto.ComposePlan;
import org.springframework.stereotype.Service;

@Service
public class ContainerizationWriter {

  private final GitHubService gitHub;

  public ContainerizationWriter(GitHubService gitHub) { this.gitHub = gitHub; }

  /** Retourne le PushResult pour historiser le commit */
  public GitHubService.PushResult ensureDockerfile(String token, String repoFullName, String branch,
                               ContainerPlan plan, GitHubService.FileHandlingStrategy strategy)
      throws java.io.IOException {
    if (!plan.isShouldGenerateDockerfile()) return null;
    return gitHub.pushWorkflowToGitHub(
      token,
      repoFullName,
      branch,
      plan.getDockerfilePath(),
      plan.getGeneratedDockerfileContent(),
      strategy
    );
  }

  public void ensureCompose(String token, String repoFullName, String branch,
                            ComposePlan composePlan, GitHubService.FileHandlingStrategy strategy)
      throws java.io.IOException {
    if (composePlan == null || !composePlan.shouldGenerateCompose) return;
    gitHub.pushWorkflowToGitHub(
      token,
      repoFullName,
      branch,
      composePlan.composePath,
      composePlan.content,
      strategy
    );
  }

  public void pushFile(String token, String repoFullName, String branch,
                       String path, String content,
                       GitHubService.FileHandlingStrategy strategy) throws java.io.IOException {
    gitHub.pushWorkflowToGitHub(token, repoFullName, branch, path, content, strategy);
  }
}
