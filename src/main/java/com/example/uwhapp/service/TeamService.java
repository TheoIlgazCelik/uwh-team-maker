package com.example.uwhapp.service;

import com.example.uwhapp.model.*;
import com.example.uwhapp.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeamService {
    private final TeamRepository teamRepo;
    private final TeamMemberRepository teamMemberRepo;
    private final RsvpService rsvpService;
    private final UserRepository userRepo;
    private final Map<String, TeamGenerator> generators;

    public TeamService(TeamRepository teamRepo,
                       TeamMemberRepository teamMemberRepo,
                       RsvpService rsvpService,
                       UserRepository userRepo,
                       List<TeamGenerator> generatorList) {
        this.teamRepo = teamRepo;
        this.teamMemberRepo = teamMemberRepo;
        this.rsvpService = rsvpService;
        this.userRepo = userRepo;
        this.generators = new HashMap<>();
        // populate by bean name; components are annotated with @Component("name")
        generatorList.forEach(g -> {
            String name = g.getClass().getAnnotation(org.springframework.stereotype.Component.class).value();
            generators.put(name, g);
        });
    }

    @Transactional
    public List<List<User>> generateAndSaveTeams(Long eventId, int teamSize, String method) {
        List<Rsvp> yes = rsvpService.findYesForEvent(eventId);
        List<Long> userIds = yes.stream().map(Rsvp::getUserId).collect(Collectors.toList());
        if (userIds.isEmpty()) return Collections.emptyList();

        List<User> attendees = userRepo.findAllById(userIds);
        TeamGenerator generator = generators.getOrDefault(method, generators.get("random"));
        List<List<User>> teams = generator.makeTeams(attendees, teamSize);

        // remove existing teams for event (simple approach)
        List<Team> existing = teamRepo.findByEventIdOrderByTeamIndex(eventId);
        if (!existing.isEmpty()) {
            List<Long> existingIds = existing.stream().map(Team::getId).collect(Collectors.toList());
            // delete members then teams
            List<TeamMember> membersToDelete = teamMemberRepo.findByTeamIdIn(existingIds);
            teamMemberRepo.deleteAll(membersToDelete);
            teamRepo.deleteAll(existing);
        }

        // persist teams
        for (int i = 0; i < teams.size(); i++) {
            Team t = new Team(eventId, i + 1);
            teamRepo.save(t);
            for (User u : teams.get(i)) {
                teamMemberRepo.save(new TeamMember(t.getId(), u.getId()));
            }
        }

        return teams;
    }

    public List<Team> getTeamsForEvent(Long eventId) {
        return teamRepo.findByEventIdOrderByTeamIndex(eventId);
    }
}
