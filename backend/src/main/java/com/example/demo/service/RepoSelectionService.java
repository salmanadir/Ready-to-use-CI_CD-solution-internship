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
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));  
        return gitHubService.getUserRepositories(user.getToken());  
    }  
  
    public Repo selectRepository(Long userId, Map<String, Object> repoData) {  
        User user = userRepository.findById(userId)  
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));  
  
        String githubRepoId = repoData.get("id").toString();  
  
        Repo repo = new Repo();  
        repo.setUser(user);  
        repo.setGithubRepoId(githubRepoId);  
        repo.setFullName((String) repoData.get("full_name"));  
        repo.setUrl((String) repoData.get("html_url"));  
        repo.setDefaultBranch((String) repoData.get("default_branch"));  
        repo.setTechnicalDetails(repoData.toString());  
  
        return repoRepository.save(repo);  
    }  
  
    public List<Repo> getUserSelectedRepos(Long userId) {  
        return repoRepository.findByUserId(userId);  
    }  
  
    // ✅ CORRIGÉ : Utiliser Long au lieu de String pour githubId  
    public User createOrUpdateUser(String token) {  
        Map<String, Object> userInfo = gitHubService.getUserInfo(token);  
        Long githubId = ((Number) userInfo.get("id")).longValue(); // ✅ Conversion correcte  
        String username = (String) userInfo.get("login");  
        String email = (String) userInfo.get("email");  
  
        User user = userRepository.findByGithubId(githubId) // ✅ Long githubId  
                .orElse(new User());  
  
        user.setGithubId(githubId); // ✅ Long githubId  
        user.setUsername(username);  
        user.setToken(token);  
        if (email != null) {  
            user.setEmail(email);  
        }  
  
        return userRepository.save(user);  
    }  
  
    public void deselectRepository(Long userId, Long repoId) {  
        List<Repo> userRepos = repoRepository.findByUserId(userId);  
  
        Repo repoToDelete = userRepos.stream()  
                .filter(repo -> repo.getRepoId().equals(repoId))  
                .findFirst()  
                .orElseThrow(() -> new RuntimeException("Repository introuvable"));  
  
        repoRepository.delete(repoToDelete);  
    }  
}