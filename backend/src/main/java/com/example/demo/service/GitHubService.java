package com.example.demo.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;  
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service  
public class GitHubService {  

    private final RestTemplate restTemplate = new RestTemplate();  
    private static final String GITHUB_API_URL = "https://api.github.com";  

   
    public List<Map<String, Object>> getUserRepositories(String token) {  
        if (token == null || token.trim().isEmpty()) {  
            throw new RuntimeException("GitHub token is required");  
        }  

        HttpHeaders headers = createHeaders(token);  
        HttpEntity<String> entity = new HttpEntity<>(headers);  

        try {  
            ResponseEntity<List> response = restTemplate.exchange(  
                    GITHUB_API_URL + "/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator",  
                    HttpMethod.GET,  
                    entity,  
                    List.class  
            );  
            return response.getBody();  
        } catch (RestClientException e) {  
            throw new RuntimeException("Error while fetching repositories: " + e.getMessage());  
        }  
    }  

    
    public Map<String, Object> getUserInfo(String token) {  
        if (token == null || token.trim().isEmpty()) {  
            throw new RuntimeException("GitHub token is required");  
        }  

        HttpHeaders headers = createHeaders(token);  
        HttpEntity<String> entity = new HttpEntity<>(headers);  

        try {  
            ResponseEntity<Map> response = restTemplate.exchange(  
                    GITHUB_API_URL + "/user",  
                    HttpMethod.GET,  
                    entity,  
                    Map.class  
            );  
            return response.getBody();  
        } catch (RestClientException e) {  
            throw new RuntimeException("Error while fetching user information: " + e.getMessage());  
        }  
    }  

   
    private HttpHeaders createHeaders(String token) {  
        HttpHeaders headers = new HttpHeaders();  
        headers.set("Authorization", "Bearer " + token);  
        headers.set("Accept", "application/vnd.github.v3+json");  
        headers.set("User-Agent", "CI-CD-Management-App");  
        return headers;  
    }  
}
