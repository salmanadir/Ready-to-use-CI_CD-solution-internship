// filepath: c:\Users\youss\Desktop\Ready-to-use-CI_CD-solution\backend\src\main\java\com\example\demo\controller\AuthController.java
package com.example.demo.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.client-secret}")
    private String clientSecret;

    @Value("${github.redirect-uri}")
    private String redirectUri;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/login")
    public ResponseEntity<?> login() {
        String githubAuthUrl = "https://github.com/login/oauth/authorize" +
                "?client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&scope=repo user:email";
        
        return ResponseEntity.status(302)
                .header("Location", githubAuthUrl)
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<?> callback(@RequestParam("code") String code) {
        try {
            // Step 1: Exchange code for access token
            String accessToken = exchangeCodeForToken(code);
            if (accessToken == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to obtain access token"));
            }

            // Step 2: Fetch user info from GitHub
            Map<String, Object> userData = fetchGitHubUserData(accessToken);
            if (userData == null || userData.get("id") == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to fetch user data"));
            }

            // Step 3: Save or update user in database
            User user = saveOrUpdateUser(userData, accessToken);

            // Step 4: Generate JWT
            String jwt = generateJWT(user);

            // Step 5: Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", jwt);
            response.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "githubId", user.getGithubId()
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                .body(Map.of("error", "Authentication failed: " + e.getMessage()));
        }
    }

    private String exchangeCodeForToken(String code) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String tokenUrl = "https://github.com/login/oauth/access_token";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json");

            String requestBody = "client_id=" + clientId +
                               "&client_secret=" + clientSecret +
                               "&code=" + code +
                               "&redirect_uri=" + redirectUri;

            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            ParameterizedTypeReference<Map<String, Object>> responseType = new ParameterizedTypeReference<Map<String, Object>>() {};
ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
    tokenUrl, 
    HttpMethod.POST, 
    request, 
    responseType
);

Map<String, Object> responseBody = response.getBody();
return responseBody != null ? (String) responseBody.get("access_token") : null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Map<String, Object> fetchGitHubUserData(String accessToken) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String userInfoUrl = "https://api.github.com/user";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Accept", "application/vnd.github.v3+json");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
            
            return response.getBody();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private User saveOrUpdateUser(Map<String, Object> userData, String accessToken) {
        Long githubId = ((Number) userData.get("id")).longValue();
        String username = (String) userData.get("login");
        String email = (String) userData.get("email");
        String avatarUrl = (String) userData.get("avatar_url");

        User user = userRepository.findByGithubId(githubId)
                .orElse(new User());
        
        user.setGithubId(githubId);
        user.setUsername(username);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        user.setToken(accessToken);

        return userRepository.save(user);
    }

    private String generateJWT(User user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        
        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("githubId", user.getGithubId())
                .claim("userId", user.getId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(key)
                .compact();
    }
}