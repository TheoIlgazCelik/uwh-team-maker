package com.example.uwhapp.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.uwhapp.model.Event;
import com.example.uwhapp.model.NotificationLog;
import com.example.uwhapp.model.Rsvp;
import com.example.uwhapp.model.Subscription;
import com.example.uwhapp.repository.EventRepository;
import com.example.uwhapp.repository.NotificationLogRepository;
import com.example.uwhapp.repository.RsvpRepository;
import com.example.uwhapp.repository.SubscriptionRepository;

@Component
public class ScheduledPushSender {

    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final WebPushService webPushService;
    private final RsvpRepository rsvpRepository;

    private static final ZoneId NZ_ZONE = ZoneId.of("Pacific/Auckland");

    public ScheduledPushSender(EventRepository eventRepository,
                               SubscriptionRepository subscriptionRepository,
                               NotificationLogRepository notificationLogRepository,
                               WebPushService webPushService,
                               RsvpRepository rsvpRepository) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.webPushService = webPushService;
        this.rsvpRepository = rsvpRepository;
    }

    // run every minute in NZ time
    @Scheduled(cron = "0 * * * * *", zone = "Pacific/Auckland")
    public void checkAndSend() {
        List<Event> events = eventRepository.findAll();
        ZonedDateTime now = ZonedDateTime.now(NZ_ZONE).withSecond(0).withNano(0);

        for (Event e : events) {
            if (e.getStartTime() == null) continue;
            ZonedDateTime evtStart = ZonedDateTime.ofInstant(e.getStartTime(), NZ_ZONE);

            // 1) Day-of 10:00
            ZonedDateTime dayOf10 = evtStart.withHour(10).withMinute(0).withSecond(0).withNano(0);
            if (isSameMinute(now, dayOf10)) {
                Optional<NotificationLog> sent = notificationLogRepository.findByEventIdAndType(e.getId(), "DAY_OF_10AM");
                if (sent.isEmpty()) {
                    sendDayOfNotification(e);
                    notificationLogRepository.save(new NotificationLog(e.getId(), "DAY_OF_10AM"));
                }
            }

            // 2) One hour before event
            ZonedDateTime oneHourBefore = evtStart.minusHours(1).withSecond(0).withNano(0);
            if (isSameMinute(now, oneHourBefore)) {
                Optional<NotificationLog> sent = notificationLogRepository.findByEventIdAndType(e.getId(), "HOUR_BEFORE");
                if (sent.isEmpty()) {
                    sendHourBeforeNotification(e);
                    notificationLogRepository.save(new NotificationLog(e.getId(), "HOUR_BEFORE"));
                }
            }
        }
    }

    private boolean isSameMinute(ZonedDateTime a, ZonedDateTime b) {
        return a.getYear()==b.getYear() && a.getDayOfYear()==b.getDayOfYear()
                && a.getHour()==b.getHour() && a.getMinute()==b.getMinute();
    }

    private void sendDayOfNotification(Event e) {
        List<Subscription> subs = subscriptionRepository.findAll();
        String payload = String.format("{\"title\":\"%s\",\"body\":\"RSVP for the event now\",\"url\":\"/events/%d\"}",
                                       escapeJson(e.getTitle()), e.getId());
        for (Subscription s : subs) {
            webPushService.sendNotification(s, payload);
        }
    }

    private void sendHourBeforeNotification(Event e) {
        // use RsvpRepository.findByEventIdAndStatus to find RSVPs with status "yes"
        List<Rsvp> yesList = rsvpRepository.findByEventIdAndStatus(e.getId(), "yes");
        List<Long> userIdsYes = yesList.stream()
                .map(Rsvp::getUserId) // assumes Rsvp has getUserId()
                .collect(Collectors.toList());

        List<Subscription> subs;
        if (userIdsYes.isEmpty()) {
            // fallback: send to all subscribers
            subs = subscriptionRepository.findAll();
        } else {
            subs = userIdsYes.stream()
                    .flatMap(uid -> subscriptionRepository.findByUserId(uid).stream())
                    .collect(Collectors.toList());
        }

        String payload = String.format("{\"title\":\"%s\",\"body\":\"Click this notification to see the teams\",\"url\":\"/events/%d\"}",
                                       escapeJson(e.getTitle()), e.getId());
        for (Subscription s : subs) {
            webPushService.sendNotification(s, payload);
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"","\\\"");
    }
}
