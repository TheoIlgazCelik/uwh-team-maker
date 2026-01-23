package com.example.uwhapp.dto;

public class GenerateTeamsRequest {
    private Integer teamSize = 5;
    private String method = "random"; // "random" or "balanced"

    public GenerateTeamsRequest() {}

    public Integer getTeamSize() { return teamSize; }
    public void setTeamSize(Integer teamSize) { this.teamSize = teamSize; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
}
