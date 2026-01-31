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

    // NO constructor injection here — prevents early dependency cycles

    public static void main(String[] args) {
        System.out.println("=== ENV at startup ===");
        System.out.println("PORT=" + System.getenv("PORT"));
        System.out.println("SPRING_PROFILES_ACTIVE=" + System.getenv("SPRING_PROFILES_ACTIVE"));
        System.out.println("DATABASE_URL=" + (System.getenv("DATABASE_URL") != null ? "[present]" : "[missing]"));
        System.out.println("SPRING_DATASOURCE_URL=" + (System.getenv("SPRING_DATASOURCE_URL") != null ? "[present]" : "[missing]"));
        System.out.println("======================");

        SpringApplication.run(UwhAppApplication.class, args);
        System.out.println("✅ Spring Boot started successfully!");
        System.out.println("http://localhost:8080/");
        System.out.println("https://uwh-team-maker-production.up.railway.app/");
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

        jdbcUrl = System.getenv("SPRING_DATASOURCE_URL");
        username = System.getenv("SPRING_DATASOURCE_USERNAME");
        password = System.getenv("SPRING_DATASOURCE_PASSWORD");

        if (jdbcUrl == null) {
            String dbUrl = System.getenv("DATABASE_URL");
            if (dbUrl != null) {
                if (dbUrl.startsWith("jdbc:")) {
                    jdbcUrl = dbUrl;
                } else if (dbUrl.startsWith("postgres://")) {
                    try {
                        URI uri = new URI(dbUrl);
                        String userInfo = uri.getUserInfo();
                        if (userInfo != null) {
                            String[] parts = userInfo.split(":", 2);
                            username = parts[0];
                            password = parts.length > 1 ? parts[1] : "";
                        }
                        String host = uri.getHost();
                        int port = uri.getPort();
                        String path = uri.getPath();
                        jdbcUrl = "jdbc:postgresql://" + host + (port != -1 ? ":" + port : "") + path;
                    } catch (Exception e) {
                        System.err.println("Failed to parse DATABASE_URL: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }

        if (jdbcUrl == null) jdbcUrl = System.getProperty("spring.datasource.url");
        if (username == null) username = System.getProperty("spring.datasource.username");
        if (password == null) password = System.getProperty("spring.datasource.password");
        if (username == null) username = System.getenv("PGUSER");
        if (password == null) password = System.getenv("PGPASSWORD");

        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            System.out.println("No JDBC URL found in env; falling back to H2 in-memory database.");
            jdbcUrl = "jdbc:h2:mem:uwhdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false";
            if (username == null) username = "sa";
            if (password == null) password = "";
        } else {
            System.out.println("Using JDBC URL: " + (jdbcUrl.startsWith("jdbc:h2:") ? jdbcUrl : "[REDACTED FOR SECURITY]"));
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        if (username != null) cfg.setUsername(username);
        if (password != null) cfg.setPassword(password);

        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setIdleTimeout(10_000);
        cfg.setConnectionTimeout(10_000);
        cfg.setPoolName("uwh-hikari");

        return new HikariDataSource(cfg);
    }

    /**
     * Seed data. AuthService is injected *here* (method param) — this avoids creating
     * the AuthService (and its repo dependencies) during configuration class construction,
     * breaking the circular dependency.
     */
    @Bean
    CommandLineRunner seedData(UserRepository userRepo,
                               EventRepository eventRepo,
                               RsvpRepository rsvpRepo,
                               AuthService authService) {
        return args -> {
            try {
                if (userRepo.count() == 0) {
                    User t = new User("Theo", "theo", 100);
                    t.setIsAdmin(true);
                    t.setPasswordHash(authService.hash("password"));
                    userRepo.save(t);
                    userRepo.save(new User("Sam", "sam", 60));
                    userRepo.save(new User("Alex", "alex", 50));
                    userRepo.save(new User("Lee", "lee", 30));
                    userRepo.save(new User("Jordan", "jord", 40));
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
            }
        };
    }
}
