package com.example.uwhapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.uwhapp.dto.PushSubscription;
import com.example.uwhapp.model.Subscription;
import com.example.uwhapp.model.User;
import com.example.uwhapp.repository.SubscriptionRepository;
import com.example.uwhapp.service.AuthService;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final SubscriptionRepository repo;
    private final AuthService authService;

    // VAPID public key will be returned to clients
    private final String vapidPublicKey;

    public PushController(SubscriptionRepository repo, AuthService authService) {
        this.repo = repo;
        this.authService = authService;
        // [Inference] I place your public key here. You can also read this from application.properties/env.
        this.vapidPublicKey = "BBNHsiY8rfQoYUWjq6rXGjKvQWBXSLM-nh_6F6XImcrxXXKmM_vDBpsQHnCEwFr6V0na4MPqayyUGgZmOIk2bW0";
    }

    @GetMapping("/vapidPublicKey")
    public ResponseEntity<?> getVapidPublicKey() {
        return ResponseEntity.ok().body(Map.of("publicKey", vapidPublicKey));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestHeader(value="X-Auth-Token", required=false) String token,
                                       @RequestBody PushSubscription sub) {
        Long userId = null;
        if (token != null) {
            Optional<User> ou = authService.findByToken(token);
            if (ou.isPresent()) userId = ou.get().getId();
        }

        // deduplicate by endpoint
        Subscription existing = repo.findByEndpoint(sub.getEndpoint());
        if (existing != null) {
            // update keys if changed
            existing.setP256dh(sub.getKeys().get("p256dh"));
            existing.setAuth(sub.getKeys().get("auth"));
            if (userId != null) existing.setUserId(userId);
            repo.save(existing);
            return ResponseEntity.ok().build();
        }

        Subscription s = new Subscription(userId, sub.getEndpoint(), sub.getKeys().get("p256dh"), sub.getKeys().get("auth"));
        repo.save(s);
        return ResponseEntity.ok().build();
    }
}
