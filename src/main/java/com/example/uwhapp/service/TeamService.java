package com.example.uwhapp.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.uwhapp.model.Rsvp;
import com.example.uwhapp.model.Team;
import com.example.uwhapp.model.TeamMember;
import com.example.uwhapp.model.User;
import com.example.uwhapp.repository.TeamMemberRepository;
import com.example.uwhapp.repository.TeamRepository;
import com.example.uwhapp.repository.UserRepository;

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
    public List<List<User>> generateAndSaveTeams(Long eventId, String method) {
        List<Rsvp> yes = rsvpService.findYesForEvent(eventId);
        List<Long> userIds = yes.stream().map(Rsvp::getUserId).collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<User> attendees = userRepo.findAllById(userIds);

        // determine number of teams
        int numPlayers = attendees.size();
        int numTeams = (numPlayers > 21) ? 4 : 2;
        int teamSize = numPlayers / numTeams;
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSavedTeams(Long eventId) {
        List<Team> teams = teamRepo.findByEventIdOrderByTeamIndex(eventId);
        if (teams.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Team t : teams) {
            List<TeamMember> members = teamMemberRepo.findByTeamId(t.getId());
            List<Long> memberIds = members.stream().map(TeamMember::getUserId).collect(Collectors.toList());
            List<User> users = memberIds.isEmpty() ? Collections.emptyList() : userRepo.findAllById(memberIds);

            List<Map<String, Object>> memberDtos = users.stream().map(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("name", u.getName());
                m.put("skill", u.getSkill());
                return m;
            }).collect(Collectors.toList());

            Map<String, Object> teamMap = new HashMap<>();
            teamMap.put("teamIndex", t.getTeamIndex());
            teamMap.put("members", memberDtos);
            out.add(teamMap);
        }
        return out;
    }

    @Transactional
    public void deleteTeamsForEvent(Long eventId) {
        List<Team> existing = teamRepo.findByEventIdOrderByTeamIndex(eventId);
        if (existing.isEmpty()) {
            return;
        }
        List<Long> existingIds = existing.stream().map(Team::getId).collect(Collectors.toList());
        List<TeamMember> membersToDelete = teamMemberRepo.findByTeamIdIn(existingIds);
        if (!membersToDelete.isEmpty()) {
            teamMemberRepo.deleteAll(membersToDelete);
        }
        teamRepo.deleteAll(existing);
    }
}
