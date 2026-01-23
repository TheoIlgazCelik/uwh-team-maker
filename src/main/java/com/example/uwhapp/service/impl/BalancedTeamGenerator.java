package com.example.uwhapp.service.impl;

import com.example.uwhapp.model.User;
import com.example.uwhapp.service.TeamGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component("balanced")
public class BalancedTeamGenerator implements TeamGenerator {
    @Override
    public List<List<User>> makeTeams(List<User> attendees, int teamSize) {
        List<User> pool = new ArrayList<>(attendees);
        pool.sort(Comparator.comparingInt(User::getSkill).reversed());
        int n = pool.size();
        int numTeams = Math.max(1, (n + teamSize - 1) / teamSize);
        List<List<User>> teams = new ArrayList<>();
        for (int i = 0; i < numTeams; i++) teams.add(new ArrayList<>());

        boolean forward = true;
        int idx = 0;
        while (idx < n) {
            if (forward) {
                for (int t = 0; t < numTeams && idx < n; t++) {
                    teams.get(t).add(pool.get(idx++));
                }
            } else {
                for (int t = numTeams - 1; t >= 0 && idx < n; t--) {
                    teams.get(t).add(pool.get(idx++));
                }
            }
            forward = !forward;
        }
        return teams;
    }
}
