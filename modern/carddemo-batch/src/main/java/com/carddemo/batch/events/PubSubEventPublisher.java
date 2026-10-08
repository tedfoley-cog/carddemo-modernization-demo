package com.carddemo.batch.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Publishes batch events to a Pub/Sub topic. Active only with the {@code pubsub} profile;
 * credentials come from the Cloud Run service account via Application Default Credentials.
 */
@Component
@Profile("pubsub")
public class PubSubEventPublisher implements EventPublisher {

    private final Publisher publisher;
    private final ObjectMapper objectMapper;

    public PubSubEventPublisher(@Value("${CARDDEMO_PUBSUB_PROJECT}") String project,
                                @Value("${CARDDEMO_PUBSUB_TOPIC}") String topic,
                                ObjectMapper objectMapper) throws IOException {
        this.publisher = Publisher.newBuilder(TopicName.of(project, topic)).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String eventType, Object payload) {
        try {
            PubsubMessage message = PubsubMessage.newBuilder()
                    .putAttributes("eventType", eventType)
                    .setData(ByteString.copyFromUtf8(objectMapper.writeValueAsString(payload)))
                    .build();
            publisher.publish(message).get(30, TimeUnit.SECONDS);
        } catch (JsonProcessingException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Failed to publish " + eventType, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing " + eventType, e);
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        publisher.shutdown();
        publisher.awaitTermination(30, TimeUnit.SECONDS);
    }
}
