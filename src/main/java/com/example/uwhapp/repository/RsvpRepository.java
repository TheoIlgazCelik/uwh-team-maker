package com.example.uwhapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.uwhapp.model.Rsvp;

public interface RsvpRepository extends JpaRepository<Rsvp, Long> {
    List<Rsvp> findByEventIdAndStatus(Long eventId, String status);
    Optional<Rsvp> findByEventIdAndUserId(Long eventId, Long userId);
    List<Rsvp> findByUserId(Long userId);
    List<Rsvp> findByEventId(Long eventId);

}
