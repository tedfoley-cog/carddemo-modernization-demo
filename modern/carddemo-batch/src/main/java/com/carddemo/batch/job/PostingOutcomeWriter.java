package com.carddemo.batch.job;

import com.carddemo.batch.posting.PostingOutcome;
import com.carddemo.batch.support.SequentialDataset;
import com.carddemo.domain.fixedwidth.CobolDecimal;
import com.carddemo.domain.repository.CardTransactionRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;

/**
 * CBTRN02C 2900-WRITE-TRANSACTION-FILE and 2500-WRITE-REJECT-REC for a chunk of outcomes.
 * <p>Requirements: BAT-POST-03 (docs/BUSINESS_REQUIREMENTS.md).
 */
public class PostingOutcomeWriter implements ItemWriter<PostingOutcome>, StepExecutionListener {

    private final CardTransactionRepository transactions;
    private final Path rejectFile;
    private SequentialDataset rejects;
    private long posted;
    private long rejected;
    private BigDecimal postedAmount = BigDecimal.ZERO;

    public PostingOutcomeWriter(CardTransactionRepository transactions, Path rejectFile) {
        this.transactions = transactions;
        this.rejectFile = rejectFile;
    }

    /** CBTRN02C 0100-TRANFILE-OPEN (OPEN OUTPUT replaces the master) and 0300-DALYREJS-OPEN. */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        transactions.deleteAllInBatch();
        rejects = SequentialDataset.openOutput(rejectFile, 430);
    }

    @Override
    public void write(Chunk<? extends PostingOutcome> chunk) {
        for (PostingOutcome outcome : chunk) {
            if (outcome.isRejected()) {
                rejects.write(outcome.rejectRecord());
                rejected++;
            } else {
                transactions.save(outcome.posted());
                postedAmount = postedAmount.add(outcome.posted().getAmount());
                posted++;
            }
        }
    }

    /** CBTRN02C end of job: RETURN-CODE 4 when any transaction was rejected. */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        rejects.close();
        ExecutionContext job = stepExecution.getJobExecution().getExecutionContext();
        job.putInt(StreamContext.RETURN_CODE, rejected > 0 ? 4 : 0);
        job.putLong(StreamContext.RECORDS, stepExecution.getReadCount());
        job.putLong("posted", posted);
        job.putLong("rejected", rejected);
        job.putString("postedAmount", CobolDecimal.s10v2(postedAmount).toPlainString());
        return stepExecution.getExitStatus();
    }
}
