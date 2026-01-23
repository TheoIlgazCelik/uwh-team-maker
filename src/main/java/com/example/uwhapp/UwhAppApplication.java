package com.example.uwhapp;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

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
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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
        System.out.println("SPRING_DATASOURCE_URL=" + (System.getenv("SPRING_DATASOURCE_URL") != null ? "[present]" : "[missing]"));
        System.out.println("======================");

        SpringApplication.run(UwhAppApplication.class, args);
        System.out.println("✅ Spring Boot started successfully!");
    }

    /**
     * Provide a DataSource bean programmatically so we bypass problematic placeholder
     * resolution that may leave ${DATABASE_URL} unresolved or in non-jdbc form.
     */
    @Bean
    public DataSource dataSource() {
        String jdbcUrl = null;
        String username = null;
        String password = null;

        // 1) Highest priority: explicit SPRING_DATASOURCE_* envs (user-friendly in PaaS)
        jdbcUrl = System.getenv("SPRING_DATASOURCE_URL");
        username = System.getenv("SPRING_DATASOURCE_USERNAME");
        password = System.getenv("SPRING_DATASOURCE_PASSWORD");

        // 2) If not explicit, try DATABASE_URL commonly provided by Railway/Heroku
        if (jdbcUrl == null) {
            String dbUrl = System.getenv("DATABASE_URL");
            if (dbUrl != null) {
                // If it's already a JDBC URL, use it directly
                if (dbUrl.startsWith("jdbc:")) {
                    jdbcUrl = dbUrl;
                } else if (dbUrl.startsWith("postgres://")) {
                    try {
                        URI uri = new URI(dbUrl);
                        String userInfo = uri.getUserInfo(); // user:pass
                        if (userInfo != null) {
                            String[] parts = userInfo.split(":", 2);
                            username = parts[0];
                            password = parts.length > 1 ? parts[1] : "";
                        }
                        String host = uri.getHost();
                        int port = uri.getPort();
                        String path = uri.getPath(); // includes leading '/'
                        jdbcUrl = "jdbc:postgresql://" + host + (port != -1 ? ":" + port : "") + path;
                    } catch (Exception e) {
                        System.err.println("Failed to parse DATABASE_URL: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }

        // 3) As a last resort, check if Spring system properties were set (e.g. via previous code),
        //    or PGUSER/PGPASSWORD envs.
        if (jdbcUrl == null) jdbcUrl = System.getProperty("spring.datasource.url");
        if (username == null) username = System.getProperty("spring.datasource.username");
        if (password == null) password = System.getProperty("spring.datasource.password");
        if (username == null) username = System.getenv("PGUSER");
        if (password == null) password = System.getenv("PGPASSWORD");

        // 4) Fallback to in-memory H2 if nothing found (keeps app running locally)
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            System.out.println("No JDBC URL found in env; falling back to H2 in-memory database.");
            jdbcUrl = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false";
            if (username == null) username = "sa";
            if (password == null) password = "";
        } else {
            System.out.println("Using JDBC URL: " + (jdbcUrl.startsWith("jdbc:h2:") ? jdbcUrl : "[REDACTED FOR SECURITY]"));
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        if (username != null) cfg.setUsername(username);
        if (password != null) cfg.setPassword(password);

        // Conservative pool settings for small PaaS instances
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setIdleTimeout(10_000);
        cfg.setConnectionTimeout(10_000);
        cfg.setPoolName("uwh-hikari");

        return new HikariDataSource(cfg);
    }

    // Seed some sample users and an event for quick testing (safe: catches exceptions)
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
                System.err.println("Seeding error: " + e.getMessage());
                e.printStackTrace();
                // swallow so the app keeps running and logs are visible
            }
        };
    }
}
