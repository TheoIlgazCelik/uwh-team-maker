package com.example.uwhapp.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.uwhapp.model.Event;
import com.example.uwhapp.repository.EventRepository;

@Component
public class ScheduledEventCreator {

    private final EventRepository eventRepository;

    private static final ZoneId NZ_ZONE = ZoneId.of("Pacific/Auckland");

    public ScheduledEventCreator(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // ===============================
    // THURSDAY — 7:30 PM
    // ===============================
    @Scheduled(cron = "0 30 19 ? * THU", zone = "Pacific/Auckland")
    public void createThursdayEvent() {
        createIfNotExists("UWH Session (Thursday)", getNextThursdayAt(19, 30));
    }

    // ===============================
    // SUNDAY — 4:30 PM
    // ===============================
    @Scheduled(cron = "0 30 16 ? * SUN", zone = "Pacific/Auckland")
    public void createSundayEvent() {
        createIfNotExists("UWH Session (Sunday)", getNextSundayAt(16, 30));
    }

    // ===============================
    // CORE CREATION LOGIC
    // ===============================
    private void createIfNotExists(String title, Instant startTime) {

        boolean exists = eventRepository
                .existsByTitleAndStartTime(title, startTime);

        if (exists) {
            System.out.println("Event already exists: " + title);
            return;
        }

        Event e = new Event();
        e.setTitle(title);
        e.setLocation("Local Pool");
        e.setStartTime(startTime);
        e.setCreatedBy(null);

        eventRepository.save(e);

        System.out.println("Created scheduled event: " + title);
    }

    // ===============================
    // DATE HELPERS
    // ===============================

    private Instant getNextThursdayAt(int hour, int minute) {
        return getNextDayAt(DayOfWeek.THURSDAY, hour, minute);
    }

    private Instant getNextSundayAt(int hour, int minute) {
        return getNextDayAt(DayOfWeek.SUNDAY, hour, minute);
    }

    private Instant getNextDayAt(DayOfWeek day, int hour, int minute) {

        ZonedDateTime now = ZonedDateTime.now(NZ_ZONE);

        ZonedDateTime next = now
                .with(TemporalAdjusters.nextOrSame(day))
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0);

        // If time already passed today, go next week
        if (next.isBefore(now)) {
            next = next.plusWeeks(1);
        }

        return next.toInstant();
    }
    public void createEventNow() { String title = "UWH Session (auto)"; String location = "Local Pool"; Instant start = Instant.now().plusSeconds(60 * 60); // one hour from now 
    Event e = new Event(); 
    e.setTitle(title); 
    e.setLocation(location); 
    e.setStartTime(start); 
    e.setCreatedBy(null); 
    eventRepository.save(e); 
}
}
