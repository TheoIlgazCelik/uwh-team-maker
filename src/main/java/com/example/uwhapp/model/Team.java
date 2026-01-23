package com.example.uwhapp.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "teams")
public class Team {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    private Integer teamIndex;

    private Instant createdAt = Instant.now();

    public Team() {}

    public Team(Long eventId, Integer teamIndex) {
        this.eventId = eventId;
        this.teamIndex = teamIndex;
    }

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public Integer getTeamIndex() { return teamIndex; }
    public void setTeamIndex(Integer teamIndex) { this.teamIndex = teamIndex; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
