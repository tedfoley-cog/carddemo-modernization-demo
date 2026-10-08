package com.carddemo.batch.job;

import com.carddemo.batch.config.CardDemoBatchProperties;
import com.carddemo.batch.report.ReportLookups;
import com.carddemo.batch.report.TransactionReport;
import com.carddemo.batch.support.LegacyAbendException;
import com.carddemo.batch.support.SequentialDataset;
import com.carddemo.domain.model.CardTransaction;
import com.carddemo.domain.model.TimestampFormat;
import com.carddemo.domain.model.TransactionCategoryId;
import com.carddemo.domain.repository.CardTransactionRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.TransactionCategoryRepository;
import com.carddemo.domain.repository.TransactionTypeRepository;
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

/** TRANREPT (app/jcl/TRANREPT.jcl STEP05R SORT INCLUDE + STEP10R CBTRN03C): BAT-RPT-01. */
@Configuration
public class TransactionReportJobConfig {

    static final int ABEND_CODE = 999;

    @Bean
    public Job transactionReportJob(JobRepository jobRepository, Step transactionReportStep) {
        return new JobBuilder("transactionReportJob", jobRepository).start(transactionReportStep).build();
    }

    @Bean
    public Step transactionReportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                      CardTransactionRepository transactions, ReportLookups reportLookups,
                                      CardDemoBatchProperties properties) {
        String start = properties.reportStartDate().toString();
        String end = properties.reportEndDate().toString();
        return new StepBuilder("transactionReportStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    try (SequentialDataset out = SequentialDataset.openOutput(
                            properties.outDir().resolve(StreamContext.TRANREPT), 133)) {
                        TransactionReport report = new TransactionReport(out, reportLookups, properties.reportStartDate(),
                                properties.reportEndDate(), properties.legacyFixes().reportNoDoubleCountLastAmount());
                        long read = 0;
                        for (CardTransaction t : transactions.findAll(Sort.by("cardNumber", "transactionId"))) {
                            String processed = t.getProcessedAt() == null ? "" : TimestampFormat.DB2.format(t.getProcessedAt()).substring(0, 10);
                            if (processed.compareTo(start) >= 0 && processed.compareTo(end) <= 0) {
                                report.accept(t);
                                read++;
                            }
                        }
                        report.finish();
                        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext()
                                .putLong(StreamContext.RECORDS, read);
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public ReportLookups reportLookups(CardXrefRepository xrefs, TransactionTypeRepository types,
                                       TransactionCategoryRepository categories) {
        return new ReportLookups() {
            @Override
            public long accountIdForCard(String cardNumber) {
                return xrefs.findById(cardNumber).orElseThrow(() ->
                        new LegacyAbendException(ABEND_CODE, "INVALID CARD NUMBER : " + cardNumber)).getAccountId();
            }

            @Override
            public String typeDescription(String typeCode) {
                return types.findById(typeCode).orElseThrow(() ->
                        new LegacyAbendException(ABEND_CODE, "INVALID TRANSACTION TYPE : " + typeCode)).getDescription();
            }

            @Override
            public String categoryDescription(String typeCode, int categoryCode) {
                return categories.findById(new TransactionCategoryId(typeCode, categoryCode)).orElseThrow(() ->
                        new LegacyAbendException(ABEND_CODE, "INVALID TRAN CATG KEY : " + typeCode + categoryCode)).getDescription();
            }
        };
    }
}
