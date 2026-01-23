package com.example.uwhapp.service.impl;

import com.example.uwhapp.model.User;
import com.example.uwhapp.service.TeamGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Component("random")
public class RandomTeamGenerator implements TeamGenerator {
    private final Random random = new Random();

    @Override
    public List<List<User>> makeTeams(List<User> attendees, int teamSize) {
        List<User> pool = new ArrayList<>(attendees);
        Collections.shuffle(pool, random);
        int n = pool.size();
        int numTeams = Math.max(1, (n + teamSize - 1) / teamSize);
        List<List<User>> teams = new ArrayList<>();
        for (int i = 0; i < numTeams; i++) teams.add(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            teams.get(i % numTeams).add(pool.get(i));
        }
        return teams;
    }
}
