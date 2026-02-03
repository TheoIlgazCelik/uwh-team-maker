package com.example.uwhapp.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.uwhapp.model.Event;
import com.example.uwhapp.model.Rsvp;
import com.example.uwhapp.model.User;
import com.example.uwhapp.repository.EventRepository;
import com.example.uwhapp.repository.RsvpRepository;
import com.example.uwhapp.repository.UserRepository;
import com.example.uwhapp.service.AuthService;
import com.example.uwhapp.service.TeamService;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepo;
    private final EventRepository eventRepo;
    private final RsvpRepository rsvpRepo;
    private final AuthService authService;
    private final TeamService teamService;

    public AdminController(UserRepository userRepo,
                           EventRepository eventRepo,
                           RsvpRepository rsvpRepo,
                           AuthService authService, TeamService teamService) {
        this.userRepo = userRepo;
        this.eventRepo = eventRepo;
        this.rsvpRepo = rsvpRepo;
        this.authService = authService;
        this.teamService = teamService;
    }

    // helper - require admin from token, throws 403 if not admin
    private User requireAdmin(String token) {
        User u = authService.findByToken(token).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token"));
        if (!Boolean.TRUE.equals(u.getIsAdmin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin only");
        }
        return u;
    }

    // List all users (without sensitive fields)
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@RequestHeader("X-Auth-Token") String token) {
        requireAdmin(token);
        List<Map<String,Object>> out = userRepo.findAll().stream().sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER)).map(u -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName());
            m.put("username", u.getUsername());
            m.put("skill", u.getSkill());
            m.put("isAdmin", Boolean.TRUE.equals(u.getIsAdmin()));
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    // Delete user
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@RequestHeader("X-Auth-Token") String token, @PathVariable Long id) {
        requireAdmin(token);
        if (!userRepo.existsById(id)) return ResponseEntity.status(404).body(Map.of("error","not found"));
        // delete RSVPs first
        List<Rsvp> rsvps = rsvpRepo.findByUserId(id);
        rsvpRepo.deleteAll(rsvps);
        userRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Update a user's skill (payload: { "skill": 42 })
    @PutMapping("/users/{id}/skill")
    public ResponseEntity<?> updateSkill(@RequestHeader("X-Auth-Token") String token,
                                         @PathVariable Long id,
                                         @RequestBody Map<String,Object> body) {
        requireAdmin(token);
        Integer skill = null;
        if (body.containsKey("skill")) {
            Object s = body.get("skill");
            skill = (s instanceof Number) ? ((Number) s).intValue() : Integer.parseInt(s.toString());
        } else {
            return ResponseEntity.badRequest().body(Map.of("error","skill required"));
        }
        User u = userRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        u.setSkill(skill);
        userRepo.save(u);
        return ResponseEntity.ok(Map.of("id", u.getId(), "skill", u.getSkill()));
    }

    // Create an event (admin)
    @PostMapping("/events")
    public ResponseEntity<?> createEvent(@RequestHeader("X-Auth-Token") String token,
                                         @RequestBody Map<String,Object> body) {
        requireAdmin(token);
        String title = Objects.toString(body.getOrDefault("title",""), null);
        String startTimeStr = Objects.toString(body.getOrDefault("startTime",""), null);
        if (title == null || title.isBlank()) return ResponseEntity.badRequest().body(Map.of("error","title required"));
        Instant start;
        if (startTimeStr == null || startTimeStr.isBlank()) start = Instant.now().plusSeconds(3600);
        else start = Instant.parse(startTimeStr);
        Event e = new Event();
        e.setTitle(title);
        e.setLocation(Objects.toString(body.getOrDefault("location",""), ""));
        e.setStartTime(start);
        Event saved = eventRepo.save(e);
        return ResponseEntity.ok(saved);
    }

    // List RSVPs for an event
    @GetMapping("/events/{eventId}/rsvps")
    public ResponseEntity<?> listRsvps(@RequestHeader("X-Auth-Token") String token, @PathVariable Long eventId) {
        requireAdmin(token);
        List<Rsvp> rsvps = rsvpRepo.findByEventId(eventId);
        return ResponseEntity.ok(rsvps);
    }
    // Admin update event (used by your front-end edit button)
    @PutMapping("/events/{eventId}")
    public ResponseEntity<?> adminUpdateEvent(@PathVariable("eventId") Long eventId,
            @RequestBody Map<String, Object> body) {
        try {
            Optional<Event> oe = eventRepo.findById(eventId);
            if (oe.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "event not found"));
            }
            Event e = oe.get();
            if (body.containsKey("title")) {
                e.setTitle(Objects.toString(body.get("title"), e.getTitle()));
            }
            if (body.containsKey("location")) {
                e.setLocation(Objects.toString(body.get("location"), e.getLocation()));
            }
            if (body.containsKey("startTime")) {
                String s = Objects.toString(body.get("startTime"), "");
                if (s != null && !s.isBlank()) {
                    try {
                        e.setStartTime(Instant.parse(s));
                    } catch (Exception ex) {
                        // ignore malformed startTime and return bad request
                        return ResponseEntity.badRequest().body(Map.of("error", "startTime must be an ISO instant, e.g. 2026-01-23T18:00:00Z"));
                    }
                }
            }
            Event saved = eventRepo.save(e);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

// Admin delete event (will also delete any saved teams via TeamService)
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<?> adminDeleteEvent(@PathVariable("eventId") Long eventId) {
        try {
            // remove saved teams & members first
            teamService.deleteTeamsForEvent(eventId);
            // then delete event
            eventRepo.deleteById(eventId);
            return ResponseEntity.noContent().build();
        } catch (EmptyResultDataAccessException ex) {
            return ResponseEntity.status(404).body(Map.of("error", "event not found"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/events/{eventId}/teams/{teamIndex}/adjust-skill")
    public ResponseEntity<?> adjustTeamSkill(@RequestHeader("X-Auth-Token") String token,
                                             @PathVariable Long eventId,
                                             @PathVariable int teamIndex,
                                             @RequestBody Map<String, Object> body) {
        requireAdmin(token);
        Integer delta = 0;
        if (body.containsKey("delta")) {
            delta = (body.get("delta") instanceof Number) ? ((Number) body.get("delta")).intValue() :
                    Integer.parseInt(body.get("delta").toString());
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "delta required"));
        }
        teamService.adjustSkillForTeam(eventId, teamIndex, delta);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/events/{eventId}/matches")
    public ResponseEntity<?> postMatches(@RequestHeader("X-Auth-Token") String token,
                                         @PathVariable Long eventId,
                                         @RequestBody Map<String, Object> body) {
        requireAdmin(token);
        // expect { "matches": [{ "teamA":1, "teamB":2, "winner":1 }, ...], "kFactor": 24 }
        List<Map<String,Object>> matchMaps = (List<Map<String,Object>>) body.get("matches");
        int kFactor = body.containsKey("kFactor") ? ((Number)body.get("kFactor")).intValue() : 24;

        List<TeamService.MatchDto> matches = new ArrayList<>();
        for (Map<String,Object> mm : matchMaps) {
            TeamService.MatchDto md = new TeamService.MatchDto();
            md.teamA = ((Number)mm.get("teamA")).intValue();
            md.teamB = ((Number)mm.get("teamB")).intValue();
            md.winner = mm.containsKey("winner") ? ((Number)mm.get("winner")).intValue() : 0;
            matches.add(md);
        }
        Map<String,Object> res = teamService.applyMatchesAndUpdateElo(eventId, matches, kFactor);
        return ResponseEntity.ok(res);
    }


    // other admin endpoints you can add later:
    // - promote/demote admin flag
    // - export users CSV
    // - force-generate teams for an event, resend notifications
}
