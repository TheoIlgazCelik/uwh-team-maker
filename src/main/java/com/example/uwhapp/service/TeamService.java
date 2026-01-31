package com.example.uwhapp.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    public void adjustSkillForTeam(Long eventId, int teamIndex, int delta) {
        List<Team> teams = teamRepo.findByEventIdOrderByTeamIndex(eventId);
        Optional<Team> tOpt = teams.stream()
                .filter(t -> t.getTeamIndex() == teamIndex)
                .findFirst();
        if (tOpt.isEmpty()) {
            return;
        }
        Team t = tOpt.get();
        List<TeamMember> members = teamMemberRepo.findByTeamId(t.getId());
        if (members.isEmpty()) {
            return;
        }
        List<Long> memberIds = members.stream().map(TeamMember::getUserId).collect(Collectors.toList());
        List<User> users = userRepo.findAllById(memberIds);
        for (User u : users) {
            Integer s = u.getSkill() == null ? 0 : u.getSkill();
            u.setSkill(s + delta);
        }
        userRepo.saveAll(users);
    }

    /**
     * MatchDto represents one pairwise game between two teams.
     */
    public static class MatchDto {

        public int teamA; // teamIndex (1-based)
        public int teamB; // teamIndex
        public int winner; // teamIndex of winner, or 0 for draw (optional)
    }

    @Transactional
    public Map<String, Object> applyMatchesAndUpdateElo(Long eventId, List<MatchDto> matches, int kFactor) {
        // load teams
        List<Team> teams = teamRepo.findByEventIdOrderByTeamIndex(eventId);
        if (teams.isEmpty()) {
            return Map.of("error", "no teams");
        }
        // map teamIndex -> Team
        Map<Integer, Team> teamIndexMap = teams.stream().collect(Collectors.toMap(Team::getTeamIndex, t -> t));

        // helper to compute average rating for a team
        java.util.function.Function<Integer, Double> teamRating = (teamIndex) -> {
            Team team = teamIndexMap.get(teamIndex);
            if (team == null) {
                return 0.0;
            }
            List<TeamMember> members = teamMemberRepo.findByTeamId(team.getId());
            if (members.isEmpty()) {
                return 0.0;
            }
            List<Long> ids = members.stream().map(TeamMember::getUserId).collect(Collectors.toList());
            List<User> users = userRepo.findAllById(ids);
            double sum = users.stream().mapToDouble(u -> (u.getSkill() == null ? 0 : u.getSkill())).sum();
            return sum / Math.max(1, users.size());
        };

        // collect all users we will modify (to batch save)
        Map<Long, User> modifiedUsers = new HashMap<>();

        for (MatchDto m : matches) {
            // validate teams
            if (!teamIndexMap.containsKey(m.teamA) || !teamIndexMap.containsKey(m.teamB)) {
                continue; // skip invalid match
            }
            double ratingA = teamRating.apply(m.teamA);
            double ratingB = teamRating.apply(m.teamB);

            // expected scores
            double expectedA = 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
            double expectedB = 1.0 / (1.0 + Math.pow(10.0, (ratingA - ratingB) / 400.0));

            double scoreA, scoreB;
            if (m.winner == 0) { // draw
                scoreA = 0.5;
                scoreB = 0.5;
            } else if (m.winner == m.teamA) {
                scoreA = 1.0;
                scoreB = 0.0;
            } else if (m.winner == m.teamB) {
                scoreA = 0.0;
                scoreB = 1.0;
            } else {
                // invalid winner index — treat as draw
                scoreA = 0.5;
                scoreB = 0.5;
            }

            double changeA = kFactor * (scoreA - expectedA);
            double changeB = kFactor * (scoreB - expectedB);

            // apply to team members equally
            Team teamObjA = teamIndexMap.get(m.teamA);
            Team teamObjB = teamIndexMap.get(m.teamB);
            List<TeamMember> membersA = teamMemberRepo.findByTeamId(teamObjA.getId());
            List<TeamMember> membersB = teamMemberRepo.findByTeamId(teamObjB.getId());

            // protect divide-by-zero
            int nA = Math.max(1, membersA.size());
            int nB = Math.max(1, membersB.size());

            double perPlayerA = changeA / nA;
            double perPlayerB = changeB / nB;

            // fetch users and update
            List<Long> idsA = membersA.stream().map(TeamMember::getUserId).collect(Collectors.toList());
            List<Long> idsB = membersB.stream().map(TeamMember::getUserId).collect(Collectors.toList());
            List<User> usersA = idsA.isEmpty() ? Collections.emptyList() : userRepo.findAllById(idsA);
            List<User> usersB = idsB.isEmpty() ? Collections.emptyList() : userRepo.findAllById(idsB);

            for (User u : usersA) {
                int cur = u.getSkill() == null ? 0 : u.getSkill();
                int newSkill = (int) Math.round(cur + perPlayerA);
                u.setSkill(newSkill);
                modifiedUsers.put(u.getId(), u);
            }
            for (User u : usersB) {
                int cur = u.getSkill() == null ? 0 : u.getSkill();
                int newSkill = (int) Math.round(cur + perPlayerB);
                u.setSkill(newSkill);
                modifiedUsers.put(u.getId(), u);
            }
        } // end matches loop

        if (!modifiedUsers.isEmpty()) {
            userRepo.saveAll(new ArrayList<>(modifiedUsers.values()));
        }

        return Map.of("updatedCount", modifiedUsers.size());
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
        int numTeams;
        if (numPlayers > 21) {
            numTeams = 4;
        } else {
            numTeams = 2;
        }
        int teamSize = (int) Math.ceil((double) numPlayers / numTeams);
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
