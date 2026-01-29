package com.example.uwhapp.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.uwhapp.dto.GenerateTeamsRequest;
import com.example.uwhapp.dto.RsvpRequest;
import com.example.uwhapp.model.Event;
import com.example.uwhapp.model.Rsvp;
import com.example.uwhapp.model.User;
import com.example.uwhapp.repository.EventRepository;
import com.example.uwhapp.repository.UserRepository;
import com.example.uwhapp.service.RsvpService;
import com.example.uwhapp.service.ScheduledEventCreator;
import com.example.uwhapp.service.TeamService;

@RestController
@RequestMapping("/events")
public class EventController {

    private final RsvpService rsvpService;
    private final TeamService teamService;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final ScheduledEventCreator scheduledEventCreator;

    public EventController(RsvpService rsvpService,
            TeamService teamService,
            UserRepository userRepository,
            EventRepository eventRepository,
            ScheduledEventCreator scheduledEventCreator) {
        this.rsvpService = rsvpService;
        this.teamService = teamService;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.scheduledEventCreator = scheduledEventCreator;
    }

    // GET /events  -> list events
    @GetMapping
    public ResponseEntity<?> listEvents() {
        List<Event> events = eventRepository.findAll();
        return ResponseEntity.ok(events);
    }

    // POST /events/create-recurring -> manual trigger to create Thu/Sun events
    @PostMapping("/create-recurring")
    public ResponseEntity<?> createRecurringNow() {
        try {
            scheduledEventCreator.createEventNow();
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "created");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    // POST /events -> create a single event
    // Accepts JSON with either { "title": "...", "startTime": "ISO_INSTANT" }
    // or fallback { "name": "...", "date": "ISO_INSTANT" }
    @PostMapping
    public ResponseEntity<?> createEvent(@RequestBody Map<String, Object> body) {
        try {
            String title = null;
            String startTimeStr = null;

            if (body.containsKey("title")) {
                title = Objects.toString(body.get("title"), null);
            }
            if (body.containsKey("startTime")) {
                startTimeStr = Objects.toString(body.get("startTime"), null);
            }

            // fallback keys
            if (title == null && body.containsKey("name")) {
                title = Objects.toString(body.get("name"), null);
            }
            if (startTimeStr == null && body.containsKey("date")) {
                startTimeStr = Objects.toString(body.get("date"), null);
            }

            if (title == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("error", "title (or name) is required");
                return ResponseEntity.badRequest().body(err);
            }

            Instant startInstant;
            if (startTimeStr == null || startTimeStr.isBlank()) {
                // default: 1 hour from now
                startInstant = Instant.now().plusSeconds(3600);
            } else {
                try {
                    startInstant = Instant.parse(startTimeStr);
                } catch (Exception ex) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("error", "startTime must be an ISO instant, e.g. 2026-01-23T18:00:00Z");
                    return ResponseEntity.badRequest().body(err);
                }
            }

            Event e = new Event();
            e.setTitle(title);
            e.setLocation(Objects.toString(body.getOrDefault("location", ""), ""));
            e.setStartTime(startInstant);

            // optional createdBy
            if (body.containsKey("createdBy")) {
                try {
                    Object cb = body.get("createdBy");
                    if (cb instanceof Number) {
                        e.setCreatedBy(((Number) cb).longValue()); 
                    }else {
                        e.setCreatedBy(Long.parseLong(cb.toString()));
                    }
                } catch (Exception ignored) {
                    /* leave createdBy null */ }
            }

            Event saved = eventRepository.save(e);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    // POST /events/{eventId}/rsvp
    @PostMapping("/{eventId}/rsvp")
    public ResponseEntity<?> rsvp(
            @PathVariable("eventId") Long eventId,
            @RequestBody RsvpRequest request) {

        if (request == null || request.getUserId() == null || request.getStatus() == null) {
            return ResponseEntity.badRequest().body("userId and status are required in body");
        }
        Rsvp saved = rsvpService.upsert(eventId, request.getUserId(), request.getStatus().toLowerCase());
        return ResponseEntity.ok(saved);
    }

    // POST /events/{eventId}/generate-teams
    @PostMapping("/{eventId}/generate-teams")
    public ResponseEntity<?> generateTeams(
            @PathVariable("eventId") Long eventId,
            @RequestBody GenerateTeamsRequest request) {

        int teamSize = (request != null && request.getTeamSize() != null) ? request.getTeamSize() : 5;
        String method = (request != null && request.getMethod() != null) ? request.getMethod().toLowerCase() : "random";

        List<List<User>> teams = teamService.generateAndSaveTeams(eventId, teamSize, method);

        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            List<User> members = teams.get(i);
            List<Map<String, Object>> memberDtos = members.stream().map(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("name", u.getName());
                m.put("skill", u.getSkill());
                return m;
            }).collect(Collectors.toList());

            Map<String, Object> teamMap = new HashMap<>();
            teamMap.put("teamIndex", i + 1);
            teamMap.put("members", memberDtos);
            out.add(teamMap);
        }

        return ResponseEntity.ok(out);
    }

    // GET /events/{eventId}/attendees  -> returns the User objects for RSVP = "yes"
    @GetMapping("/{eventId}/attendees")
    public ResponseEntity<?> attendees(@PathVariable("eventId") Long eventId) {
        List<Rsvp> yes = rsvpService.findYesForEvent(eventId);
        List<Long> ids = yes.stream().map(Rsvp::getUserId).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<User> users = userRepository.findAllById(ids);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{eventId}/teams")
    public ResponseEntity<?> getSavedTeams(@PathVariable("eventId") Long eventId) {
        try {
            List<Map<String, Object>> teams = teamService.getSavedTeams(eventId);
            return ResponseEntity.ok(teams);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

}
