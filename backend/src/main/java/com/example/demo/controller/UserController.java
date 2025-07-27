package com.example.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.example.demo.model.User;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/test/api/user")
public class UserController {

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(Authentication authentication) {
        // Get authenticated user from security context
        User user = (User) authentication.getPrincipal();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail() != null ? user.getEmail() : "",
            "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
            "githubId", user.getGithubId()
        ));
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(Authentication authentication, @RequestBody Map<String, Object> updates) {
        User user = (User) authentication.getPrincipal();
        
        // Mock update - in real implementation, you'd update the database
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Profile updated successfully");
        response.put("updated_fields", updates.keySet());
        response.put("user_id", user.getId());
        response.put("username", user.getUsername());
        
        return ResponseEntity.ok(response);
    }
}