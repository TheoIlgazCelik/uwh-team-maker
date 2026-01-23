package com.example.uwhapp.service;

import com.example.uwhapp.model.User;

import java.util.List;

public interface TeamGenerator {
    // returns list of teams; each team is a list of users
    List<List<User>> makeTeams(List<User> attendees, int teamSize);
}
