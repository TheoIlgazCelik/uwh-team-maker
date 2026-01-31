package com.example.uwhapp.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.uwhapp.model.User;
import com.example.uwhapp.repository.UserRepository;
import com.example.uwhapp.service.AuthService;

@RestController
public class UserController {
    private final AuthService authService;
    private final UserRepository userRepository;

    public UserController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String username = body.get("username").toLowerCase();
        String password = body.get("password");
        if (name == null || username == null || password == null) return ResponseEntity.badRequest().body("name,username,password required");
        User u = authService.register(name, username, password);
        return ResponseEntity.ok(u);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username").toLowerCase();
        String password = body.get("password");
        if (username == null || password == null) return ResponseEntity.badRequest().body("username,password required");
        try {
            String token = authService.login(username, password);
            User u = userRepository.findByUsername(username).get();
            return ResponseEntity.ok(Map.of("token", token, "user", Map.of("id", u.getId(), "name", u.getName(), "username", u.getUsername())));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(401).body(ex.getMessage());
        }
    }

    @GetMapping("/auth/me")
public ResponseEntity<Map<String,Object>> me(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
    return authService.findByToken(token)
            .map(u -> {
                Map<String,Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("name", u.getName());
                m.put("username", u.getUsername().toLowerCase());
                m.put("isAdmin", Boolean.TRUE.equals(u.getIsAdmin()));
                return ResponseEntity.ok(m);
            })
            .orElseGet(() -> {
                Map<String,Object> err = new HashMap<>();
                err.put("error", "invalid token");
                return ResponseEntity.status(401).body(err);
            });
}


}