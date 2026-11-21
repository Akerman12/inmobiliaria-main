package com.example.inmobiliaria;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class ChatbotRateLimiterTest {

    @Test
    void aplicaLimitesPorVisitanteYUsuarioYSeReiniciaTrasVentana() {
        AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-21T10:00:00Z"));
        Clock reloj = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return ahora.get();
            }
        };

        var limiter = new TestableRateLimiter(2, 4, reloj);

        assertTrue(limiter.tryConsume("IP:127.0.0.1", false));
        assertTrue(limiter.tryConsume("IP:127.0.0.1", false));
        assertFalse(limiter.tryConsume("IP:127.0.0.1", false));

        assertTrue(limiter.tryConsume("USER:u1", true));
        assertTrue(limiter.tryConsume("USER:u1", true));
        assertTrue(limiter.tryConsume("USER:u1", true));
        assertTrue(limiter.tryConsume("USER:u1", true));
        assertFalse(limiter.tryConsume("USER:u1", true));

        ahora.set(ahora.get().plusSeconds(61));
        assertTrue(limiter.tryConsume("IP:127.0.0.1", false));
        assertTrue(limiter.tryConsume("USER:u1", true));
    }

    private static final class TestableRateLimiter extends com.example.inmobiliaria.chat.ChatbotRateLimiter {
        TestableRateLimiter(int visitorLimit, int userLimit, Clock clock) {
            super(visitorLimit, userLimit, clock);
        }
    }
}

