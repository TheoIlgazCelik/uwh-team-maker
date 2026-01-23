package com.example.uwhapp;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.uwhapp.model.Event;
import com.example.uwhapp.model.Rsvp;
import com.example.uwhapp.model.User;
import com.example.uwhapp.repository.EventRepository;
import com.example.uwhapp.repository.RsvpRepository;
import com.example.uwhapp.repository.UserRepository;
import com.example.uwhapp.service.AuthService;

@SpringBootApplication
@EnableScheduling
public class UwhAppApplication {

    private final AuthService authService;

    UwhAppApplication(AuthService authService) {
        this.authService = authService;
    }

    public static void main(String[] args) {
        System.out.println("=== ENV at startup ===");
        System.out.println("PORT=" + System.getenv("PORT"));
        System.out.println("SPRING_PROFILES_ACTIVE=" + System.getenv("SPRING_PROFILES_ACTIVE"));
        System.out.println("DATABASE_URL=" + (System.getenv("DATABASE_URL") != null ? "[present]" : "[missing]"));
        System.out.println("======================");

        // Convert a common 'postgres://user:pass@host:port/db' style URL to JDBC format
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl != null && dbUrl.startsWith("postgres://")) {
            try {
                URI uri = new URI(dbUrl);
                String userInfo = uri.getUserInfo();
                String username = null;
                String password = null;
                if (userInfo != null) {
                    String[] parts = userInfo.split(":", 2);
                    username = parts[0];
                    password = parts.length > 1 ? parts[1] : "";
                }

                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath(); // includes leading '/'
                String jdbcUrl = "jdbc:postgresql://" + host + (port != -1 ? ":" + port : "") + path;

                if (username != null) System.setProperty("spring.datasource.username", username);
                if (password != null) System.setProperty("spring.datasource.password", password);
                System.setProperty("spring.datasource.url", jdbcUrl);

                System.out.println("Converted DATABASE_URL -> JDBC URL: " + jdbcUrl);
            } catch (Exception e) {
                System.err.println("Failed to parse DATABASE_URL: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("AFTER CODE DATABASE_URL=" + (System.getenv("DATABASE_URL") != null ? "[present]" : "[missing]"));

        SpringApplication.run(UwhAppApplication.class, args);
        System.out.println("✅ Spring Boot started successfully!");
    }

    // Seed some sample users and an event for quick testing
    @Bean
    CommandLineRunner seedData(UserRepository userRepo, EventRepository eventRepo, RsvpRepository rsvpRepo) {
        return args -> {
            try {
                if (userRepo.count() == 0) {
                    User t = new User("Theo", "theo@example.com", 100);
                    t.setIsAdmin(true);
                    t.setPasswordHash(authService.hash("password"));
                    userRepo.save(t);
                    userRepo.save(new User("Sam", "sam@example.com", 60));
                    userRepo.save(new User("Alex", "alex@example.com", 50));
                    userRepo.save(new User("Lee", "lee@example.com", 30));
                    userRepo.save(new User("Jordan", "jord@example.com", 40));
                }
                if (eventRepo.count() == 0) {
                    Event e = new Event();
                    e.setTitle("Tonight - UWH Session");
                    e.setLocation("Local Pool");
                    e.setStartTime(Instant.now().plusSeconds(3600));
                    e.setCreatedBy(1L);
                    eventRepo.save(e);
                }
                // RSVPS
                if (rsvpRepo.count() == 0) {
                    Event event = eventRepo.findAll().get(0);
                    List<User> users = userRepo.findAll();
                    for (int i = 0; i < users.size(); i++) {
                        User u = users.get(i);
                        String status;
                        if (i == 0) status = "yes";
                        else if (i == 1) status = "yes";
                        else if (i == 2) status = "maybe";
                        else if (i == 3) status = "no";
                        else status = "maybe";

                        Rsvp r = new Rsvp(event.getId(), u.getId(), status);
                        rsvpRepo.save(r);
                    }
                }
            } catch (Exception e) {
                System.out.println("Seeding error: " + e.getMessage());
                e.printStackTrace();
                // swallow the exception so the app keeps running and logs are visible
            }
        };
    }
}
