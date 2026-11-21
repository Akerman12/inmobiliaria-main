package com.example.inmobiliaria.chat;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChatbotRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_TRACKED_KEYS = 5_000;

    private final ConcurrentHashMap<String, Deque<Long>> requestsByKey = new ConcurrentHashMap<>();
    private final int visitorLimitPerMinute;
    private final int userLimitPerMinute;
    private final Clock clock;

    @Autowired
    public ChatbotRateLimiter(
            @Value("${chatbot.rate-limit.visitor-per-minute:8}") int visitorLimitPerMinute,
            @Value("${chatbot.rate-limit.user-per-minute:15}") int userLimitPerMinute) {
        this(visitorLimitPerMinute, userLimitPerMinute, Clock.systemUTC());
    }

    public ChatbotRateLimiter(int visitorLimitPerMinute, int userLimitPerMinute, Clock clock) {
        this.visitorLimitPerMinute = Math.max(1, visitorLimitPerMinute);
        this.userLimitPerMinute = Math.max(1, userLimitPerMinute);
        this.clock = clock;
    }

    public boolean tryConsume(String key, boolean authenticated) {
        if (key == null || key.isBlank()) {
            return false;
        }
        long now = clock.millis();
        long cutoff = now - WINDOW_MILLIS;
        int limit = authenticated ? userLimitPerMinute : visitorLimitPerMinute;

        if (requestsByKey.size() > MAX_TRACKED_KEYS) {
            pruneExpiredKeys(cutoff);
        }

        Deque<Long> timestamps = requestsByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private void pruneExpiredKeys(long cutoff) {
        requestsByKey.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            }
        });
    }
}
