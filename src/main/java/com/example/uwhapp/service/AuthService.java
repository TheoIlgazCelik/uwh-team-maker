package com.example.uwhapp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.uwhapp.model.User;
import com.example.uwhapp.repository.UserRepository;

@Service
public class AuthService {
    private final UserRepository userRepo;

    public AuthService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // Hash password using SHA-256 (demo only; use BCrypt in real apps). [Inference]
    public String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public User register(String name, String username, String plainPassword) {
        if (userRepo.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already registered");
        }
        User u = new User();
        u.setName(name);
        u.setUsername(username);
        u.setPasswordHash(hash(plainPassword));
        u.setSkill(50);
        return userRepo.save(u);
    }

    public String login(String username, String plainPassword) {
        Optional<User> ou = userRepo.findByUsername(username);
        if (ou.isEmpty()) throw new IllegalArgumentException("Invalid credentials");
        User u = ou.get();
        if (!u.getPasswordHash().equals(hash(plainPassword))) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        String token = UUID.randomUUID().toString();
        u.setToken(token);
        userRepo.save(u);
        return token;
    }

    public Optional<User> findByToken(String token) {
        if (token == null) return Optional.empty();
        return userRepo.findByToken(token);
    }
}
