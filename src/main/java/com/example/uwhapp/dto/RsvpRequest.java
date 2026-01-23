package com.example.uwhapp.dto;

public class RsvpRequest {
    private Long userId;
    private String status; // "yes","no","maybe"

    public RsvpRequest() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
