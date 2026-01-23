package com.example.uwhapp.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.uwhapp.model.Event;

public interface EventRepository extends JpaRepository<Event, Long> {
    boolean existsByTitleAndStartTime(String title, Instant startTime);
}
