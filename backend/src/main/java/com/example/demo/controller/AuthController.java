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
import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;

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
    private final ObjectMapper objectMapper = new ObjectMapper(); // remember this line in front
    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/login")
    public ResponseEntity<?> login() {
        String githubAuthUrl = "https://github.com/login/oauth/authorize" +
                "?client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&scope=repo workflow user:email";

        return ResponseEntity.status(302)
                .header("Location", githubAuthUrl)
                .build();
    }

    @GetMapping("backend/test/delete/callback")
    public ResponseEntity<?> callback_backend(@RequestParam("code") String code) {
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
    @GetMapping("/callback")
public ResponseEntity<?> callback(@RequestParam("code") String code) {
    try {
        // Step 1: Exchange code for access token (your existing logic)
        String accessToken = exchangeCodeForToken(code);
        if (accessToken == null) {
            // Redirect to frontend with error
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "http://localhost:3000/auth/callback?error=Failed to obtain access token")
                .build();
        }

        // Step 2: Fetch user info from GitHub (your existing logic)
        Map<String, Object> userData = fetchGitHubUserData(accessToken);
        if (userData == null || userData.get("id") == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "http://localhost:3000/auth/callback?error=Failed to fetch user data")
                .build();
        }

        // Step 3: Save or update user in database (your existing logic)
        User user = saveOrUpdateUser(userData, accessToken);

        // Step 4: Generate JWT (your existing logic)
        String jwt = generateJWT(user);

        // Step 5: Create user data for frontend
        Map<String, Object> userForFrontend = Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail() != null ? user.getEmail() : "",
            "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
            "githubId", user.getGithubId()
        );

        // Step 6: Redirect to frontend with token and user data
        try {
            String userJson = objectMapper.writeValueAsString(userForFrontend);
            String redirectUrl = "http://localhost:5173/auth/callback" + 
                "?token=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8) + 
                "&user=" + URLEncoder.encode(userJson, StandardCharsets.UTF_8);
                
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", redirectUrl)
                .build();
                
        } catch (Exception jsonException) {
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "http://localhost:5173/auth/callback?error=Failed to process user data")
                .build();
        }

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.FOUND)
            .header("Location", "http://localhost:5173/auth/callback?error=" + URLEncoder.encode("Authentication failed: " + e.getMessage(), StandardCharsets.UTF_8))
            .build();
    }
}
    @PostMapping("/refresh")
public ResponseEntity<Map<String, Object>> refreshToken(Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    String newJwt = generateJWT(user);  // Generate fresh token
    
    return ResponseEntity.ok(Map.of(
        "success", true,
        "token", newJwt,
        "message", "Token refreshed"
    ));
}
    @DeleteMapping("/delete-account")
    public ResponseEntity<Map<String, Object>> deleteAccount(Authentication authentication) {
    try {
        User user = (User) authentication.getPrincipal();
        
        // Step 1: Revoke GitHub token
        revokeGitHubToken(user.getToken());
        
        // Step 2: Delete user completely
        userRepository.delete(user);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Account deleted successfully");
        response.put("deleted_user", user.getUsername());
        
        return ResponseEntity.ok(response);
        
    } catch (Exception e) {
        return ResponseEntity.status(500)
            .body(Map.of(
                "success", false,
                "error", "Failed to delete account: " + e.getMessage()
            ));
    }
    }
    private boolean revokeGitHubToken(String accessToken) {
    if (accessToken == null || accessToken.isEmpty()) {
        return true; // Nothing to revoke
    }
    
    try {
        RestTemplate restTemplate = new RestTemplate();
        
        // GitHub's token revocation endpoint
        String revokeUrl = "https://api.github.com/applications/" + clientId + "/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(clientId, clientSecret); // Basic auth with client credentials
        
        Map<String, String> requestBody = Map.of("access_token", accessToken);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<String> response = restTemplate.exchange(
            revokeUrl, 
            HttpMethod.DELETE, 
            request, 
            String.class
        );
        // GitHub returns 204 No Content on successful revocation
        return response.getStatusCode().is2xxSuccessful();
        
    } catch (Exception e) {
        // Log error but don't fail the entire operation
        System.err.println("Failed to revoke GitHub token: " + e.getMessage());
        return false;
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