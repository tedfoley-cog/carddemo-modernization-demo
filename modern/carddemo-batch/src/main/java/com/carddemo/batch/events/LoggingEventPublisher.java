package com.carddemo.batch.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Default publisher: writes the event to the job log (Cloud Logging on Cloud Run). */
@Component
@Profile("!pubsub")
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(String eventType, Object payload) {
        log.info("event {} {}", eventType, payload);
    }
}
