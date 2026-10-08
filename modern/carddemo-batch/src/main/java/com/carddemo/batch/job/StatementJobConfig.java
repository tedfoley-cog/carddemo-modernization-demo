package com.carddemo.batch.job;

import com.carddemo.batch.config.CardDemoBatchProperties;
import com.carddemo.batch.statement.StatementGenerator;
import com.carddemo.batch.support.SequentialDataset;
import com.carddemo.domain.copybook.CardTransactionCodec;
import com.carddemo.domain.fixedwidth.FixedWidth;
import com.carddemo.domain.model.CardXref;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardTransactionRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

/** CREASTMT (app/jcl/CREASTMT.JCL STEP010 SORT, STEP020 REPRO, STEP040 CBSTM03A/CBSTM03B): BAT-STM-01. */
@Configuration
public class StatementJobConfig {

    @Bean
    public Job statementJob(JobRepository jobRepository, Step statementStep) {
        return new JobBuilder("statementJob", jobRepository).start(statementStep).build();
    }

    @Bean
    public Step statementStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              CardTransactionRepository transactions, CardXrefRepository xrefs,
                              AccountRepository accounts, CustomerRepository customers,
                              CardDemoBatchProperties properties) {
        CardTransactionCodec codec = new CardTransactionCodec();
        StatementGenerator.Lookups lookups = new StatementGenerator.Lookups() {
            @Override
            public java.util.Optional<com.carddemo.domain.model.Customer> customer(long customerId) {
                return customers.findById(customerId);
            }

            @Override
            public java.util.Optional<com.carddemo.domain.model.Account> account(long accountId) {
                return accounts.findById(accountId);
            }
        };
        return new StepBuilder("statementStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // CREASTMT STEP010 SORT FIELDS=(TRAN-CARD-NUM,TRAN-ID) with OUTREC card + record
                    List<String> trnx = transactions.findAll(Sort.by("cardNumber", "transactionId")).stream()
                            .map(codec::encode)
                            .map(r -> FixedWidth.padRight(r.substring(262, 278) + r.substring(0, 262) + r.substring(278, 328), 350))
                            .toList();
                    List<CardXref> cardXrefs = xrefs.findAll(Sort.by("cardNumber"));
                    if (properties.legacyFixes().skipOrphanXref()) {
                        cardXrefs = cardXrefs.stream().filter(x -> accounts.existsById(x.getAccountId())
                                && customers.existsById(x.getCustomerId())).toList();
                    }
                    try (SequentialDataset stmt = SequentialDataset.openOutput(properties.outDir().resolve(StreamContext.STATEMNT), 80);
                         SequentialDataset html = SequentialDataset.openOutput(properties.outDir().resolve(StreamContext.STATEMNT_HTML), 100)) {
                        int statements = new StatementGenerator(lookups, properties.legacyFixes().unboundedStatementTable(),
                                properties.legacyFixes().escapeStatementHtml())
                                .generate(trnx, cardXrefs.iterator(), stmt, html);
                        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                                .putLong(StreamContext.RECORDS, statements);
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
