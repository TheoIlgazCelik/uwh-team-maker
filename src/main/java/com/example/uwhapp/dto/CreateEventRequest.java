package com.example.uwhapp.dto;

public class CreateEventRequest {
    private String title;
    private String location;
    private Long createdBy;
    private String startTime; // optional ISO instant string

    public CreateEventRequest() {}

    // getters & setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
}
