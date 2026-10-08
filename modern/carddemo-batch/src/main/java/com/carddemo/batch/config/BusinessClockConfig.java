package com.carddemo.batch.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Business clock that replaces FUNCTION CURRENT-DATE in every program of the stream. */
@Configuration
public class BusinessClockConfig {

    @Bean
    public Clock businessClock(CardDemoBatchProperties properties) {
        return Clock.fixed(properties.businessTimestamp().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
