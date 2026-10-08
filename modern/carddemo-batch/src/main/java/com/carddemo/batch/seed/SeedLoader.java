package com.carddemo.batch.seed;

import com.carddemo.domain.copybook.AccountCodec;
import com.carddemo.domain.copybook.CardCodec;
import com.carddemo.domain.copybook.CardXrefCodec;
import com.carddemo.domain.copybook.CategoryBalanceCodec;
import com.carddemo.domain.copybook.CustomerCodec;
import com.carddemo.domain.copybook.DisclosureGroupCodec;
import com.carddemo.domain.copybook.RecordCodec;
import com.carddemo.domain.copybook.TransactionCategoryCodec;
import com.carddemo.domain.copybook.TransactionTypeCodec;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardTransactionRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.DisclosureGroupRepository;
import com.carddemo.domain.repository.TransactionCategoryBalanceRepository;
import com.carddemo.domain.repository.TransactionCategoryRepository;
import com.carddemo.domain.repository.TransactionTypeRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Initial load of the master files from seed data (the harness's IDCAMS DEFINE + REPRO). */
@Component
public class SeedLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final SeedFiles seeds;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final CardXrefRepository xrefs;
    private final CustomerRepository customers;
    private final DisclosureGroupRepository disclosureGroups;
    private final TransactionCategoryBalanceRepository categoryBalances;
    private final TransactionCategoryRepository categories;
    private final TransactionTypeRepository types;
    private final CardTransactionRepository transactions;

    public SeedLoader(SeedFiles seeds, AccountRepository accounts, CardRepository cards, CardXrefRepository xrefs,
                      CustomerRepository customers, DisclosureGroupRepository disclosureGroups,
                      TransactionCategoryBalanceRepository categoryBalances, TransactionCategoryRepository categories,
                      TransactionTypeRepository types, CardTransactionRepository transactions) {
        this.seeds = seeds;
        this.accounts = accounts;
        this.cards = cards;
        this.xrefs = xrefs;
        this.customers = customers;
        this.disclosureGroups = disclosureGroups;
        this.categoryBalances = categoryBalances;
        this.categories = categories;
        this.types = types;
        this.transactions = transactions;
    }

    @Transactional
    public void load() {
        List.of(transactions, categoryBalances, disclosureGroups, categories, types, customers, xrefs, cards, accounts)
                .forEach(JpaRepository::deleteAllInBatch);
        load("acctdata.txt", new AccountCodec(), accounts);
        load("carddata.txt", new CardCodec(), cards);
        load("cardxref.txt", new CardXrefCodec(), xrefs);
        load("custdata.txt", new CustomerCodec(), customers);
        load("discgrp.txt", new DisclosureGroupCodec(), disclosureGroups);
        load("tcatbal.txt", new CategoryBalanceCodec(), categoryBalances);
        load("trancatg.txt", new TransactionCategoryCodec(), categories);
        load("trantype.txt", new TransactionTypeCodec(), types);
    }

    private <T> void load(String file, RecordCodec<T> codec, JpaRepository<T, ?> repository) {
        List<T> rows = seeds.records(file, codec.layout().length()).stream().map(codec::decode).toList();
        repository.saveAll(rows);
        log.info("Loaded {} {} records from {}", rows.size(), codec.layout().copybook(), file);
    }
}
