package com.carddemo.batch.job;

import com.carddemo.batch.config.CardDemoBatchProperties;
import com.carddemo.domain.copybook.CardTransactionCodec;
import com.carddemo.domain.fixedwidth.FixedWidth;
import com.carddemo.domain.model.CardTransaction;
import com.carddemo.domain.repository.CardTransactionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * COMBTRAN (app/jcl/COMBTRAN.jcl STEP05R SORT + STEP10 REPRO): BAT-CMB-01. The SORT/REPRO pair
 * merges the SYSTRAN hand-off into the transaction master in TRAN-ID order; with a keyed table
 * that is an insert of the system transactions, ordering being a property of the key.
 */
@Configuration
public class CombineTransactionsJobConfig {

    @Bean
    public Job combineTransactionsJob(JobRepository jobRepository, Step combineTransactionsStep) {
        return new JobBuilder("combineTransactionsJob", jobRepository).start(combineTransactionsStep).build();
    }

    @Bean
    public Step combineTransactionsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                        CardTransactionRepository transactions, CardDemoBatchProperties properties) {
        CardTransactionCodec codec = new CardTransactionCodec();
        return new StepBuilder("combineTransactionsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Path systran = properties.outDir().resolve(StreamContext.SYSTRAN);
                    List<CardTransaction> system = Files.exists(systran)
                            ? FixedWidth.readFixedRecords(systran, codec.layout().length()).stream().map(codec::decode).toList()
                            : List.of();
                    transactions.saveAll(system);
                    chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                            .putLong(StreamContext.RECORDS, system.size());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
