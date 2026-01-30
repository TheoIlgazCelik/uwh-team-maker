package com.example.uwhapp.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_log", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_id", "type"})
})
public class NotificationLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    /**
     * type examples: "DAY_OF_10AM", "HOUR_BEFORE"
     */
    private String type;

    private Instant sentAt = Instant.now();

    public NotificationLog() {}

    public NotificationLog(Long eventId, String type) {
        this.eventId = eventId;
        this.type = type;
    }

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
