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

    public User register(String name, String email, String plainPassword) {
        if (userRepo.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(hash(plainPassword));
        u.setSkill(0);
        return userRepo.save(u);
    }

    public String login(String email, String plainPassword) {
        Optional<User> ou = userRepo.findByEmail(email);
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
