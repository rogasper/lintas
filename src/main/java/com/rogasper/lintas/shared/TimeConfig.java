package com.rogasper.lintas.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable clock. Never call {@code Instant.now()} inside domain code — the drills on
 * quote expiry and callbacks are otherwise untestable.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
