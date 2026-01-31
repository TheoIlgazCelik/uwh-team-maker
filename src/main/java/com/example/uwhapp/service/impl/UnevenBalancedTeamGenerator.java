package com.example.uwhapp.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.example.uwhapp.model.User;
import com.example.uwhapp.service.TeamGenerator;

/**
 * Creates 4 teams: two "good" teams (higher-skilled players) and two "bad" teams (lower-skilled players).
 * - Works only when the computed number of teams (based on teamSize) is 4; otherwise throws IllegalArgumentException.
 * - The top half of players (by skill) are distributed between teams 0 and 1 (good teams) in an alternating fashion
 *   to keep those two teams balanced.
 * - The bottom half are distributed between teams 2 and 3 (bad teams) in an alternating fashion to keep those two
 *   teams balanced as well.
 * This results in two internally-balanced "good" teams and two internally-balanced "bad" teams, but the good
 * teams collectively will be stronger than the bad teams.
 */
@Component("uneven-balanced")
public class UnevenBalancedTeamGenerator implements TeamGenerator {

    @Override
    public List<List<User>> makeTeams(List<User> attendees, int teamSize) {

        List<User> pool = new ArrayList<>(attendees);
        int n = pool.size();

        int computedNumTeams = Math.max(1, (n + teamSize - 1) / teamSize);
        if (computedNumTeams != 4) {
            throw new IllegalArgumentException(
                "UnevenBalancedTeamGenerator supports exactly 4 teams."
            );
        }

        pool.sort(Comparator.comparingInt(User::getSkill).reversed());

        List<List<User>> teams = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            teams.add(new ArrayList<>());
        }

        int[] teamSkill = new int[4];

        int goodCount = (n + 1) / 2;

        // Good players → teams 0 & 1 (greedy balance)
        for (int i = 0; i < goodCount; i++) {

            int target = (teamSkill[0] <= teamSkill[1]) ? 0 : 1;

            User u = pool.get(i);

            teams.get(target).add(u);
            teamSkill[target] += u.getSkill();
        }

        // Bad players → teams 2 & 3 (greedy balance)
        for (int i = goodCount; i < n; i++) {

            int target = (teamSkill[2] <= teamSkill[3]) ? 2 : 3;

            User u = pool.get(i);

            teams.get(target).add(u);
            teamSkill[target] += u.getSkill();
        }

        return teams;
    }
}
