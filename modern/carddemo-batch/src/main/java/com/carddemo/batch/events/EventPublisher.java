package com.carddemo.batch.events;

/** Outbound port for batch hand-off events. */
public interface EventPublisher {

    void publish(String eventType, Object payload);
}
