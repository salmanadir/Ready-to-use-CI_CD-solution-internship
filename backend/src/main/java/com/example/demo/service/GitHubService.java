package com.example.demo.service;

import org.kohsuke.github.GHContentUpdateResponse;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class GitHubService {

    /**
     * Push un fichier workflow vers GitHub
     * @param token Token GitHub de l'utilisateur
     * @param repoFullName Nom complet du repository (owner/repo)
     * @param branch Branche cible
     * @param filePath Chemin du fichier dans le repository
     * @param content Contenu du fichier
     * @return Le SHA du commit créé
     * @throws IOException En cas d'erreur de communication avec GitHub
     */
    public String pushWorkflowToGitHub(String token, String repoFullName, String branch, String filePath, String content) throws IOException {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("GitHub token cannot be null or empty");
        }
        if (repoFullName == null || repoFullName.isEmpty()) {
            throw new IllegalArgumentException("Repository full name cannot be null or empty");
        }
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        if (content == null) {
            throw new IllegalArgumentException("Content cannot be null");
        }

        GitHub github = GitHub.connectUsingOAuth(token);
        GHRepository repository = github.getRepository(repoFullName);
        
        GHContentUpdateResponse commitResponse = repository.createContent()
                .path(filePath)
                .content(content)
                .branch(branch != null ? branch : "main") // Défaut à "main" si branch est null
                .message("Add CI workflow file")
                .commit();
        
        return commitResponse.getCommit().getSha();
    }
}