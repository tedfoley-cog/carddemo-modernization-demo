package com.carddemo.batch.job;

import com.carddemo.batch.config.CardDemoBatchProperties;
import com.carddemo.batch.interest.InterestCalculationService;
import com.carddemo.batch.support.SequentialDataset;
import com.carddemo.domain.copybook.CardTransactionCodec;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** INTCALC (app/jcl/INTCALC.jcl STEP15, program CBACT04C, PARM=2022071800): BAT-INT-01..03. */
@Configuration
public class InterestCalculationJobConfig {

    @Bean
    public Job interestCalculationJob(JobRepository jobRepository, Step interestCalculationStep) {
        return new JobBuilder("interestCalculationJob", jobRepository).start(interestCalculationStep).build();
    }

    /** CBACT04C 0400-TRANFILE-OPEN (SYSTRAN, OPEN OUTPUT) through 9400-TRANFILE-CLOSE. */
    @Bean
    public Step interestCalculationStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                        InterestCalculationService service, CardDemoBatchProperties properties) {
        CardTransactionCodec codec = new CardTransactionCodec();
        return new StepBuilder("interestCalculationStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    try (SequentialDataset systran = SequentialDataset.openOutput(
                            properties.outDir().resolve(StreamContext.SYSTRAN), 350)) {
                        long read = service.calculate(t -> systran.write(codec.encode(t)));
                        contribution.incrementReadCount();
                        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                                .putLong(StreamContext.RECORDS, read);
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
