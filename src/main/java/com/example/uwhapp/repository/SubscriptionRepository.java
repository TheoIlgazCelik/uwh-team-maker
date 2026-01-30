package com.example.uwhapp.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.uwhapp.model.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserId(Long userId);
    List<Subscription> findAll(); // convenience
    Subscription findByEndpoint(String endpoint);
}
