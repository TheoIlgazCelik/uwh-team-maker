package com.example.uwhapp.service;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider; // your JPA entity
import org.springframework.stereotype.Service;

import com.example.uwhapp.model.Subscription;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription.Keys;

/**
 * WebPushService - sends notifications using nl.martijndwars:web-push (5.1.2).
 * Note: we avoid importing nl.martijndwars.webpush.Subscription at top-level
 * to prevent name collision with your model.Subscription entity.
 */
@Service
public class WebPushService {

    private final PushService pushService;
    private final String vapidPublicKey;
    private final String vapidPrivateKey;

    public WebPushService() throws Exception {
        this.vapidPublicKey  = "BBNHsiY8rfQoYUWjq6rXGjKvQWBXSLM-nh_6F6XImcrxXXKmM_vDBpsQHnCEwFr6V0na4MPqayyUGgZmOIk2bW0";
        this.vapidPrivateKey = "SGXg6pd4SNg0z3UxZz6UYzJAfngBw8RcdybI6zPHemQ";

        // Ensure crypto provider present
        Security.addProvider(new BouncyCastleProvider());

        pushService = new PushService();
        pushService.setPublicKey(vapidPublicKey);
        pushService.setPrivateKey(vapidPrivateKey);
        pushService.setSubject("mailto:notifications@example.com");
    }

    /**
     * Send a push to a stored Subscription entity (your model).
     * The web-push library accepts a String payload (JSON/text).
     */
    public void sendNotification(Subscription s, String payloadJson) {
        try {
            // build web-push Subscription object (fully-qualified to avoid name collision)
            nl.martijndwars.webpush.Subscription webSub =
                new nl.martijndwars.webpush.Subscription(
                    s.getEndpoint(),
                    new Keys(s.getP256dh(), s.getAuth())
                );

            // Notification constructor expects a String (payload) in this library version.
            // Pass JSON text (or null) — service worker will read event.data.json() if provided.
            String payload = payloadJson; // can be null

            Notification notification = new Notification(webSub, payload);
            pushService.send(notification);
        } catch (Exception e) {
            // endpoints expire or fail; log and optionally delete invalid subscriptions later
            System.err.println("Failed to send push to endpoint " + s.getEndpoint() + " : " + e.getMessage());
        }
    }
}
