package com.example.uwhapp.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.uwhapp.model.User;
import com.example.uwhapp.service.TeamGenerator;

@Component("balanced")
public class BalancedTeamGenerator implements TeamGenerator {

    @Override
    public List<List<User>> makeTeams(List<User> attendees, int teamSize) {

        List<User> pool = new ArrayList<>(attendees);

        // Sort strongest → weakest
        pool.sort(Comparator.comparingInt(User::getSkill).reversed());

        int n = pool.size();
        int numTeams = Math.max(1, (n + teamSize - 1) / teamSize);

        // Create teams
        List<List<User>> teams = new ArrayList<>();
        for (int i = 0; i < numTeams; i++) {
            teams.add(new ArrayList<>());
        }

        // Track current skill per team
        int[] teamSkill = new int[numTeams];

        // Assign each player to weakest team
        for (User user : pool) {

            // Find weakest team
            int minIndex = 0;

            for (int i = 1; i < numTeams; i++) {
                if (teamSkill[i] < teamSkill[minIndex]) {
                    minIndex = i;
                }
            }

            // Assign
            teams.get(minIndex).add(user);
            teamSkill[minIndex] += user.getSkill();
        }

        return teams;
    }
}
