package com.example.demo.service;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class GitHubService {

    public void pushWorkflowToGitHub(String token, String repoFullName, String branch, String filePath, String content) throws IOException {
        GitHub github = GitHub.connectUsingOAuth(token);
        GHRepository repository = github.getRepository(repoFullName);

        repository.createContent()
            .path(filePath)
            .content(content)
            .branch(branch)
            .message("Add CI workflow file")
            .commit();
    }
}
