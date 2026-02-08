package com.example.uwhapp.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import com.example.uwhapp.repository.UserRepository;

@Component
public class ScheduledPushSender {

    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final WebPushService webPushService;
    private final RsvpService rsvpService;
    private final RsvpRepository rsvpRepository;
    private final TeamService teamService;

    private static final ZoneId NZ_ZONE = ZoneId.of("Pacific/Auckland");

    // put the logger near the top of the class:
private static final Logger logger = LoggerFactory.getLogger(ScheduledPushSender.class);

    public ScheduledPushSender(EventRepository eventRepository,
            SubscriptionRepository subscriptionRepository,
            NotificationLogRepository notificationLogRepository,
            WebPushService webPushService,
            RsvpRepository rsvpRepository, TeamService teamService, 
            UserRepository userRepository, RsvpService rsvpService) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.webPushService = webPushService;
        this.rsvpService = rsvpService;
        this.rsvpRepository = rsvpRepository;
        this.teamService = teamService;
    }

    // add this method to the class:
@Scheduled(cron = "0 * * * * *", zone = "Pacific/Auckland") // every minute at second 0
public void sendToUser36EveryMinute() {
    final Long targetUserId = 36L;

    try {
        List<Subscription> subs = subscriptionRepository.findByUserId(targetUserId);
        if (subs == null || subs.isEmpty()) {
            logger.debug("No subscriptions found for user {}", targetUserId);
            return;
        }

        String payload = String.format("{\"title\":\"%s\",\"body\":\"%s\",\"url\":\"%s\"}",
                escapeJson("Minute reminder"),
                escapeJson("This notification is sent every minute to user 36"),
                escapeJson("/"));

        for (Subscription s : subs) {
            try {
                webPushService.sendNotification(s, payload);
            } catch (Exception ex) {
                // log individual failures but keep sending to other subscriptions
                logger.warn("Failed to send push to subscription id={} for user {}: {}", 
                            s.getId(), targetUserId, ex.toString());
            }
        }
        logger.info("Sent minute reminder to user {} ({} subscriptions)", targetUserId, subs.size());
    } catch (Exception e) {
        // protect the scheduler from throwing and stopping
        logger.error("Error while sending minute reminder to user " + targetUserId, e);
    }


    // run every 10 minutes in NZ time
    @Scheduled(cron = "0 */10 * * * *", zone = "Pacific/Auckland")
    public void checkAndSend() {
        List<Event> events = eventRepository.findAll();
        ZonedDateTime now = ZonedDateTime.now(NZ_ZONE).withSecond(0).withNano(0);

        final int INTERVAL_MINUTES = 10; // must match cron step
        // Window: [prevRun, now] — include times from the last INTERVAL_MINUTES
        ZonedDateTime prevRun = now.minusMinutes(INTERVAL_MINUTES);

        for (Event e : events) {
            if (e.getStartTime() == null) continue;
            ZonedDateTime evtStart = ZonedDateTime.ofInstant(e.getStartTime(), NZ_ZONE);

            // 1) Day-of 10:00
            ZonedDateTime dayOf10 = evtStart.withHour(10).withMinute(0).withSecond(0).withNano(0);
            if (isInWindow(prevRun, now, dayOf10)) {
                Optional<NotificationLog> sent = notificationLogRepository.findByEventIdAndType(e.getId(), "DAY_OF_10AM");
                if (sent.isEmpty()) {
                    sendDayOfNotification(e);
                    notificationLogRepository.save(new NotificationLog(e.getId(), "DAY_OF_10AM"));
                }
            }

            // 2) One hour before event
            ZonedDateTime oneHourBefore = evtStart.minusHours(1).withSecond(0).withNano(0);
            if (isInWindow(prevRun, now, oneHourBefore)) {
// --- Generate balanced teams (once) before sending hour-before notification
                Optional<NotificationLog> teamsGenerated = notificationLogRepository.findByEventIdAndType(e.getId(), "TEAMS_GENERATED");
                if (teamsGenerated.isEmpty()) {
                    try {
                        List<Rsvp> yes = rsvpService.findYesForEvent(e.getId());
                        String method = "Balanced";
                        

                        System.out.println("Auto-generating teams for event" + e.getId() + "using method " +  method);
                        teamService.generateAndSaveTeams(e.getId(), method);
                        notificationLogRepository.save(new NotificationLog(e.getId(), "TEAMS_GENERATED"));
                    } catch (Exception ex) {
                        // ensure generation failure doesn't stop notifications
                        System.out.println("Failed to generate teams for event " + e.getId() + "\nException : " + ex);
                    }
                }

                Optional<NotificationLog> sent = notificationLogRepository.findByEventIdAndType(e.getId(), "HOUR_BEFORE");
                if (sent.isEmpty()) {
                    sendHourBeforeNotification(e);
                    notificationLogRepository.save(new NotificationLog(e.getId(), "HOUR_BEFORE"));
                }
            }
        }
    }

    private boolean isInWindow(ZonedDateTime startInclusive, ZonedDateTime endInclusive, ZonedDateTime candidate) {
        if (candidate == null) return false;
        return !candidate.isBefore(startInclusive) && !candidate.isAfter(endInclusive);
    }

    private boolean isSameMinute(ZonedDateTime a, ZonedDateTime b) {
        return a.getYear()==b.getYear() && a.getDayOfYear()==b.getDayOfYear()
                && a.getHour()==b.getHour() && a.getMinute()==b.getMinute();
    }

    private void sendDayOfNotification(Event e) {
    // get all users who already responded (any status)
    List<Rsvp> responded = rsvpRepository.findByEventId(e.getId());
    Set<Long> respondedUserIds = responded.stream()
            .map(Rsvp::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    // load all subscriptions, then exclude those whose userId is in respondedUserIds
    List<Subscription> allSubs = subscriptionRepository.findAll();
    List<Subscription> subsToNotify;
    if (respondedUserIds.isEmpty()) {
        subsToNotify = allSubs; // nobody responded -> notify everyone
    } else {
        subsToNotify = allSubs.stream()
                .filter(s -> s.getUserId() == null || !respondedUserIds.contains(s.getUserId()))
                .collect(Collectors.toList());
    }

    String payload = String.format("{\"title\":\"%s\",\"body\":\"RSVP for the event now\",\"url\":\"/\"}",
            escapeJson(e.getTitle()));
    for (Subscription s : subsToNotify) {
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

        String payload = String.format("{\"title\":\"%s\",\"body\":\"Click this notification to see the teams\",\"url\":\"/\"}",
                escapeJson(e.getTitle()));
        for (Subscription s : subs) {
            webPushService.sendNotification(s, payload);
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"","\\\"");
    }
}
