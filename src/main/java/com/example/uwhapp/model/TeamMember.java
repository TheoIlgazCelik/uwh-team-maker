package com.example.uwhapp.model;

import jakarta.persistence.*;

@Entity
@Table(name = "team_members")
public class TeamMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "user_id")
    private Long userId;

    public TeamMember() {}

    public TeamMember(Long teamId, Long userId) {
        this.teamId = teamId;
        this.userId = userId;
    }

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
