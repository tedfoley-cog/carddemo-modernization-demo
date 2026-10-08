package com.carddemo.online.config;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Fixed business clock shared with the legacy region (default 2022-07-06 10:00:00). */
@Component
public class BusinessClock {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.000000");
    private final LocalDateTime fixed;

    public BusinessClock(CardDemoProperties props) {
        String c = props.clock();
        this.fixed = c == null || c.isBlank() || "system".equalsIgnoreCase(c) ? null : LocalDateTime.parse(c);
    }

    public LocalDateTime now() {
        return fixed != null ? fixed : LocalDateTime.now().withNano(0);
    }

    /** COBIL00C GET-CURRENT-TIMESTAMP: FORMATTIME YYYY-MM-DD + HH:MM:SS, microseconds zeroed. */
    public String legacyTimestamp() {
        return now().format(TS);
    }
}
