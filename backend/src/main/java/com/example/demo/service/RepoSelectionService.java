package com.example.demo.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.Repo;
import com.example.demo.model.User;
import com.example.demo.repository.RepoRepository;
import com.example.demo.repository.UserRepository;

@Service
@Transactional
public class RepoSelectionService {

    @Autowired
    private RepoRepository repoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitHubService gitHubService;

    public List<Map<String, Object>> getUserGithubRepos(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return gitHubService.getUserRepositories(user.getToken());
    }

    public Repo selectRepository(Long userId, Map<String, Object> repoData) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String githubRepoId = repoData.get("id").toString();

        Repo repo = new Repo();
        repo.setUser(user);
        repo.setGithubRepoId(githubRepoId);
        repo.setFullName((String) repoData.get("full_name"));
        repo.setUrl((String) repoData.get("html_url"));
        repo.setDefaultBranch((String) repoData.get("default_branch"));

        return repoRepository.save(repo);
    }

    public List<Repo> getUserSelectedRepos(Long userId) {
        return repoRepository.findByUserId(userId);
    }

    public void deselectRepository(Long userId, Long repoId) {
        List<Repo> userRepos = repoRepository.findByUserId(userId);

        Repo repoToDelete = userRepos.stream()
                .filter(repo -> repo.getRepoId().equals(repoId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        repoRepository.delete(repoToDelete);
    }
}
