package com.carddemo.batch.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.TestProperties;
import com.carddemo.batch.events.EventPublisher;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;

class TransactionsPostedPublisherTest {

    private final Clock clock = Clock.fixed(TestProperties.BUSINESS_TS.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Test
    void publishesTransactionsPostedAfterCompletedJob() {
        List<String> published = new ArrayList<>();
        JobExecution execution = completedExecution();
        PostTransactionsJobConfig.transactionsPostedPublisher((type, payload) -> published.add(type),
                TestProperties.legacy(), clock).afterJob(execution);
        assertThat(published).containsExactly("transactions-posted");
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void publishFailureFailsTheJob() {
        EventPublisher failing = (type, payload) -> {
            throw new IllegalStateException("Pub/Sub unavailable");
        };
        JobExecution execution = completedExecution();
        PostTransactionsJobConfig.transactionsPostedPublisher(failing, TestProperties.legacy(), clock).afterJob(execution);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
        assertThat(execution.getAllFailureExceptions()).singleElement()
                .satisfies(e -> assertThat(e).hasMessage("Pub/Sub unavailable"));
    }

    @Test
    void doesNotPublishWhenJobFailed() {
        List<String> published = new ArrayList<>();
        JobExecution execution = new JobExecution(1L);
        execution.setStatus(BatchStatus.FAILED);
        PostTransactionsJobConfig.transactionsPostedPublisher((type, payload) -> published.add(type),
                TestProperties.legacy(), clock).afterJob(execution);
        assertThat(published).isEmpty();
    }

    private static JobExecution completedExecution() {
        JobExecution execution = new JobExecution(1L);
        execution.setStatus(BatchStatus.COMPLETED);
        return execution;
    }
}
