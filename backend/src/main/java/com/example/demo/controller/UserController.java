package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userRepository.save(user);
    }
    @GetMapping("/{user_id}") // Doit matcher EXACTEMENT l'URL
    public ResponseEntity<User> getUserById(
        @PathVariable("user_id") Long userId) { // Nom paramètre = variable URL
        
        return userRepository.findUserById(userId)
               .map(ResponseEntity::ok)
               .orElse(ResponseEntity.notFound().build());
    }
}
