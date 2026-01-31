package com.example.uwhapp.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String username;
    private Integer skill = 0;
    @Column(name = "password_hash")
    private String passwordHash;
    private String token;
    private Instant createdAt = Instant.now();
    @Column(name = "is_admin")
    private Boolean isAdmin = false;

    public User() {}

    public User(String name, String username, Integer skill) {
        this.name = name;
        this.username = username;
        this.skill = skill == null ? 0 : skill;
    }

    // getters & setters
    // inside class User (add these fields and methods)

    public Boolean getIsAdmin() { return isAdmin; }
    public void setIsAdmin(Boolean isAdmin) { this.isAdmin = isAdmin; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getSkill() { return skill; }
    public void setSkill(Integer skill) { this.skill = skill; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
