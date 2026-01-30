package com.example.uwhapp.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.uwhapp.dto.PushSubscription;
import com.example.uwhapp.model.Subscription;
import com.example.uwhapp.model.User;
import com.example.uwhapp.repository.SubscriptionRepository;
import com.example.uwhapp.service.AuthService;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionRepository repo;
    private final AuthService authService; // [Inference] you have this service

    public SubscriptionController(SubscriptionRepository repo, AuthService authService) {
        this.repo = repo;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> save(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestBody PushSubscription dto) {

        Long userId = null;
        if (token != null) {
            Optional<User> ou = authService.findByToken(token);
            if (ou.isPresent()) userId = ou.get().getId();
        }

        // reuse existing subscription row if endpoint already exists
        Subscription s = repo.findByEndpoint(dto.getEndpoint());
        if (s == null) s = new Subscription();

        s.setEndpoint(dto.getEndpoint());
        // dto.getKeys() is a Map<String,String>, so use .get("p256dh") not getP256dh()
        Map<String, String> keys = dto.getKeys();
        if (keys != null) {
            s.setP256dh(keys.get("p256dh"));
            s.setAuth(keys.get("auth"));
        }
        s.setUserId(userId);

        repo.save(s);

        System.out.println("Saved push subscription: " + s.getEndpoint());
        return ResponseEntity.ok().build();
    }
}
