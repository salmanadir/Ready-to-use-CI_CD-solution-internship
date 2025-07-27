package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/auth/**").permitAll() // Allow all auth endpoints
                .requestMatchers("/api/public/**").permitAll() // Allow public endpoints
                .anyRequest().authenticated() // Require authentication for everything else
            )
            .csrf(csrf -> csrf.disable()) // Disable CSRF for API endpoints
            .formLogin(form -> form.disable()) // Disable default form login
            .httpBasic(basic -> basic.disable()); // Disable basic auth

        return http.build();
    }
}