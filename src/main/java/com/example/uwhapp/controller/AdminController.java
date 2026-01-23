package com.example.uwhapp.controller;

import com.example.uwhapp.model.Event;
import com.example.uwhapp.model.Rsvp;
import com.example.uwhapp.model.User;
import com.example.uwhapp.repository.EventRepository;
import com.example.uwhapp.repository.RsvpRepository;
import com.example.uwhapp.repository.UserRepository;
import com.example.uwhapp.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepo;
    private final EventRepository eventRepo;
    private final RsvpRepository rsvpRepo;
    private final AuthService authService;

    public AdminController(UserRepository userRepo,
                           EventRepository eventRepo,
                           RsvpRepository rsvpRepo,
                           AuthService authService) {
        this.userRepo = userRepo;
        this.eventRepo = eventRepo;
        this.rsvpRepo = rsvpRepo;
        this.authService = authService;
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
        List<Map<String,Object>> out = userRepo.findAll().stream().map(u -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getName());
            m.put("email", u.getEmail());
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

    // other admin endpoints you can add later:
    // - promote/demote admin flag
    // - export users CSV
    // - force-generate teams for an event, resend notifications
}
