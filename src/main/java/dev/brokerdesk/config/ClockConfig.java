package dev.brokerdesk.config;

import org.springframework.context.annotation.*;

import java.time.Clock;

/** Time only through an injected Clock, so tests can fix it. */
@Configuration
public class ClockConfig { @Bean Clock clock() { return Clock.systemUTC(); } }
