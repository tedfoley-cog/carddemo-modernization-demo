package com.carddemo.batch.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.events.EventPublisher;
import com.carddemo.batch.events.TransactionsPostedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** Runs the whole stream on the shipped seed data (H2, PostgreSQL mode) and checks its contract. */
@SpringBootTest(properties = "carddemo.batch.out-dir=target/it-out/base")
@ActiveProfiles("test")
class NightlyStreamTest {

    static final List<Object> EVENTS = new ArrayList<>();

    @TestConfiguration
    static class RecordingPublisher {
        @Bean
        @Primary
        EventPublisher recordingEventPublisher() {
            return (type, payload) -> EVENTS.add(payload);
        }
    }

    @Autowired
    NightlyStreamRunner runner;

    @Test
    void runsAllJobsInJclOrderAndWritesParityArtifacts() throws Exception {
        StreamResult result = runner.run();

        assertThat(result.exitCode()).isZero();
        assertThat(result.jobs()).extracting(StreamResult.JobResult::job).containsExactly(NightlyStream.values());
        Path out = Path.of("target/it-out/base");
        String codes = Files.readString(out.resolve("RETURN-CODES.txt"));
        assertThat(codes).hasSize(4 * 80)
                .startsWith("POSTTRAN.STEP15.CBTRN02C                RC=0004")
                .contains("CREASTMT.STEP040.CBSTM03A               RC=0000");
        assertThat(Files.size(out.resolve("TRANSACT.dat")) % 350).isZero();
        assertThat(Files.size(out.resolve("STATEMNT.HTML")) % 100).isZero();
        assertThat(EVENTS).singleElement().isInstanceOfSatisfying(TransactionsPostedEvent.class, e -> {
            assertThat(e.transactionsRead()).isEqualTo(300);
            assertThat(e.transactionsPosted() + e.transactionsRejected()).isEqualTo(300);
            assertThat(e.returnCode()).isEqualTo(4);
        });
    }
}
