package com.carddemo.batch.job;

import com.carddemo.batch.config.CardDemoBatchProperties;
import com.carddemo.batch.events.EventPublisher;
import com.carddemo.batch.events.TransactionsPostedEvent;
import com.carddemo.batch.posting.DailyTransaction;
import com.carddemo.batch.posting.PostingOutcome;
import com.carddemo.batch.posting.TransactionPostingService;
import com.carddemo.batch.seed.SeedFiles;
import com.carddemo.domain.copybook.CardTransactionCodec;
import com.carddemo.domain.repository.CardTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Clock;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** POSTTRAN (JCL app/jcl/POSTTRAN.jcl STEP15, program CBTRN02C): BAT-POST-01..03. */
@Configuration
public class PostTransactionsJobConfig {

    static final int CHUNK_SIZE = 500;

    @Bean
    public Job postTransactionsJob(JobRepository jobRepository, Step postTransactionsStep,
                                   EventPublisher eventPublisher, CardDemoBatchProperties properties, Clock clock) {
        return new JobBuilder("postTransactionsJob", jobRepository)
                .start(postTransactionsStep)
                .listener(transactionsPostedPublisher(eventPublisher, properties, clock))
                .build();
    }

    @Bean
    public Step postTransactionsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                     ListItemReader<DailyTransaction> dailyTransactionReader,
                                     TransactionPostingService postingService, PostingOutcomeWriter postingOutcomeWriter) {
        return new StepBuilder("postTransactionsStep", jobRepository)
                .<DailyTransaction, PostingOutcome>chunk(CHUNK_SIZE, transactionManager)
                .reader(dailyTransactionReader)
                .processor(postingService::process)
                .writer(postingOutcomeWriter)
                .listener(postingOutcomeWriter)
                .build();
    }

    /** CBTRN02C 1000-DALYTRAN-GET-NEXT over the DALYTRAN feed. */
    @Bean
    @StepScope
    public ListItemReader<DailyTransaction> dailyTransactionReader(SeedFiles seeds) {
        CardTransactionCodec codec = new CardTransactionCodec();
        return new ListItemReader<>(seeds.records("dailytran.txt", codec.layout().length()).stream()
                .map(raw -> new DailyTransaction(raw, codec.decode(raw)))
                .toList());
    }

    @Bean
    @StepScope
    public PostingOutcomeWriter postingOutcomeWriter(CardTransactionRepository transactions, CardDemoBatchProperties properties) {
        return new PostingOutcomeWriter(transactions, properties.outDir().resolve(StreamContext.DALYREJS));
    }

    /** Publishes the "transactions-posted" hand-off once POSTTRAN has completed. */
    static JobExecutionListener transactionsPostedPublisher(EventPublisher publisher, CardDemoBatchProperties properties,
                                                            Clock clock) {
        return new JobExecutionListener() {
            @Override
            public void afterJob(JobExecution execution) {
                if (execution.getStatus() != BatchStatus.COMPLETED) {
                    return;
                }
                ExecutionContext ctx = execution.getExecutionContext();
                publisher.publish(TransactionsPostedEvent.TYPE, new TransactionsPostedEvent(
                        properties.jobName(), LocalDateTime.now(clock), ctx.getLong(StreamContext.RECORDS, 0),
                        ctx.getLong("posted", 0), ctx.getLong("rejected", 0),
                        new BigDecimal(ctx.getString("postedAmount", "0")), ctx.getInt(StreamContext.RETURN_CODE, 0)));
            }
        };
    }
}
