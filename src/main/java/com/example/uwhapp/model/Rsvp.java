package com.example.uwhapp.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rsvps", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"}))
public class Rsvp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "user_id")
    private Long userId;

    // "yes", "no", "maybe"
    private String status = "maybe";

    private Instant respondedAt = Instant.now();

    public Rsvp() {}

    public Rsvp(Long eventId, Long userId, String status) {
        this.eventId = eventId;
        this.userId = userId;
        this.status = status;
    }

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
}
