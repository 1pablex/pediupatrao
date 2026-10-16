package com.umc.pediupatrao.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Relógio injetável: permite regras que dependem de data/hora. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
