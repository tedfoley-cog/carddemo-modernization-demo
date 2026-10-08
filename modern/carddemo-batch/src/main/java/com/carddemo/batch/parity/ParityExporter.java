package com.carddemo.batch.parity;

import com.carddemo.batch.support.SequentialDataset;
import com.carddemo.domain.copybook.AccountCodec;
import com.carddemo.domain.copybook.CardTransactionCodec;
import com.carddemo.domain.copybook.CategoryBalanceCodec;
import com.carddemo.domain.copybook.RecordCodec;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardTransactionRepository;
import com.carddemo.domain.repository.TransactionCategoryBalanceRepository;
import java.nio.file.Path;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Unloads the master tables in VSAM key order and legacy record layout for golden comparison. */
@Component
public class ParityExporter {

    private final AccountRepository accounts;
    private final TransactionCategoryBalanceRepository categoryBalances;
    private final CardTransactionRepository transactions;

    public ParityExporter(AccountRepository accounts, TransactionCategoryBalanceRepository categoryBalances,
                          CardTransactionRepository transactions) {
        this.accounts = accounts;
        this.categoryBalances = categoryBalances;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public void exportMasters(Path outDir) {
        export(outDir.resolve("ACCTDATA.dat"), new AccountCodec(), accounts.findAll(Sort.by("id")));
        export(outDir.resolve("TCATBALF.dat"), new CategoryBalanceCodec(),
                categoryBalances.findAll(Sort.by("id.accountId", "id.typeCode", "id.categoryCode")));
        export(outDir.resolve("TRANSACT.dat"), new CardTransactionCodec(), transactions.findAll(Sort.by("transactionId")));
    }

    private static <T> void export(Path file, RecordCodec<T> codec, List<T> rows) {
        try (SequentialDataset ds = SequentialDataset.openOutput(file, codec.layout().length())) {
            rows.forEach(row -> ds.write(codec.encode(row)));
        }
    }
}
