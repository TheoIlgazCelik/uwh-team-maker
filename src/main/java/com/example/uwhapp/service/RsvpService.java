package com.example.uwhapp.service;

import com.example.uwhapp.model.Rsvp;
import com.example.uwhapp.repository.RsvpRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RsvpService {
    private final RsvpRepository rsvpRepository;

    public RsvpService(RsvpRepository rsvpRepository) {
        this.rsvpRepository = rsvpRepository;
    }

    public Rsvp upsert(Long eventId, Long userId, String status) {
        Rsvp r = rsvpRepository.findByEventIdAndUserId(eventId, userId)
                .orElse(new Rsvp(eventId, userId, status));
        r.setStatus(status);
        r.setRespondedAt(Instant.now());
        return rsvpRepository.save(r);
    }

    public List<Rsvp> findYesForEvent(Long eventId) {
        return rsvpRepository.findByEventIdAndStatus(eventId, "yes");
    }
}
