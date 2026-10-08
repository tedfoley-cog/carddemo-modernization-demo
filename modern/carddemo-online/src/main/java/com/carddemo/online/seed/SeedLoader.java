package com.carddemo.online.seed;

import com.carddemo.online.config.CardDemoProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the SAME fixed-width datasets the legacy CICS region serves, so both sides start from
 * identical data. Resolution order per dataset (mirrors legacy-runtime/catalog.py):
 * {@code <workdir>/out/NAME.dat} (post-batch export) -> {@code <workdir>/ds/NAME.PS} (seed) ->
 * {@code app/data/ASCII/*.txt} -> {@code app/data/EBCDIC/*} (code page 037).
 */
@Component
public class SeedLoader {
    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);
    private static final Charset CP037 = Charset.forName("IBM037");

    record Dataset(String name, int lrecl, String asciiSrc, String ebcdicSrc) {
    }

    static final Dataset ACCTDATA = new Dataset("ACCTDATA", 300, "acctdata.txt", null);
    static final Dataset CARDDATA = new Dataset("CARDDATA", 150, "carddata.txt", null);
    static final Dataset CARDXREF = new Dataset("CARDXREF", 50, "cardxref.txt", null);
    static final Dataset CUSTDATA = new Dataset("CUSTDATA", 500, "custdata.txt", null);
    static final Dataset DISCGRP = new Dataset("DISCGRP", 50, "discgrp.txt", null);
    static final Dataset TCATBALF = new Dataset("TCATBALF", 50, "tcatbal.txt", null);
    static final Dataset TRANCATG = new Dataset("TRANCATG", 60, "trancatg.txt", null);
    static final Dataset TRANTYPE = new Dataset("TRANTYPE", 60, "trantype.txt", null);
    static final Dataset TRANSACT = new Dataset("TRANSACT", 350, null, "AWS.M2.CARDDEMO.DALYTRAN.PS.INIT");
    static final Dataset USRSEC = new Dataset("USRSEC", 80, null, "AWS.M2.CARDDEMO.USRSEC.PS");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final CardDemoProperties props;

    public SeedLoader(JdbcTemplate jdbc, PasswordEncoder encoder, CardDemoProperties props) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.props = props;
    }

    public boolean isEmpty() {
        Integer n = jdbc.queryForObject("select count(*) from account", Integer.class);
        return n == null || n == 0;
    }

    @Transactional
    public LoadSummary reload() {
        jdbc.execute("truncate table account, customer, card, card_xref, transaction, user_security,"
                + " tran_type, tran_category, tran_cat_balance, disclosure_group");
        LoadSummary s = new LoadSummary();
        s.put("ACCTDATA", load(ACCTDATA, "insert into account values (?,?,?,?,?,?,?,?,?,?,?,?,0)", r -> new Object[] {
            r.n(11), r.x(1), r.s(12, 2), r.s(12, 2), r.s(12, 2), r.x(10), r.x(10), r.x(10),
            r.s(12, 2), r.s(12, 2), r.x(10), r.x(10)}));
        s.put("CUSTDATA", load(CUSTDATA, "insert into customer values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                r -> new Object[] {r.n(9), r.x(25), r.x(25), r.x(25), r.x(50), r.x(50), r.x(50), r.x(2), r.x(3),
                    r.x(10), r.x(15), r.x(15), r.n(9), r.x(20), r.x(10), r.x(10), r.x(1), (int) r.n(3)}));
        s.put("CARDDATA", load(CARDDATA, "insert into card values (?,?,?,?,?,?,0)", r -> new Object[] {
            r.x(16), r.n(11), (int) r.n(3), r.x(50), r.x(10), r.x(1)}));
        s.put("CARDXREF", load(CARDXREF, "insert into card_xref values (?,?,?)", r -> new Object[] {
            r.x(16), r.n(9), r.n(11)}));
        s.put("TRANSACT", load(TRANSACT, "insert into transaction values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                r -> new Object[] {r.x(16), r.x(2), (int) r.n(4), r.x(10), r.x(100), r.s(11, 2), r.n(9), r.x(50),
                    r.x(50), r.x(10), r.x(16), r.x(26), r.x(26)}));
        s.put("USRSEC", load(USRSEC, "insert into user_security values (?,?,?,?,?)", r -> {
            String id = r.x(8);
            String first = r.x(20);
            String last = r.x(20);
            String pwd = r.x(8);
            return new Object[] {id, first, last, encoder.encode(pwd.toUpperCase(Locale.ROOT)), r.x(1)};
        }));
        s.put("TRANTYPE", load(TRANTYPE, "insert into tran_type values (?,?)", r -> new Object[] {r.x(2), r.x(50)}));
        s.put("TRANCATG", load(TRANCATG, "insert into tran_category values (?,?,?)", r -> new Object[] {
            r.x(2), (int) r.n(4), r.x(50)}));
        s.put("TCATBALF", load(TCATBALF, "insert into tran_cat_balance values (?,?,?,?)", r -> new Object[] {
            r.n(11), r.x(2), (int) r.n(4), r.s(11, 2)}));
        s.put("DISCGRP", load(DISCGRP, "insert into disclosure_group values (?,?,?,?)", r -> new Object[] {
            r.x(10), r.x(2), (int) r.n(4), r.s(6, 2)}));
        log.info("Seed data loaded: {}", s);
        return s;
    }

    private int load(Dataset ds, String sql, Function<FixedWidthRecord, Object[]> mapper) {
        List<Object[]> rows = new ArrayList<>();
        for (String rec : records(ds)) {
            if (!rec.isBlank()) {
                rows.add(mapper.apply(new FixedWidthRecord(rec)));
            }
        }
        jdbc.batchUpdate(sql, rows);
        return rows.size();
    }

    List<String> records(Dataset ds) {
        try {
            String workdir = props.seed().workdir();
            if (workdir != null && !workdir.isBlank()) {
                Path out = Path.of(workdir, "out", ds.name() + ".dat");
                Path ps = Path.of(workdir, "ds", ds.name() + ".PS");
                for (Path p : List.of(out, ps)) {
                    if (Files.isRegularFile(p)) {
                        log.info("{} <- {}", ds.name(), p);
                        return fixed(Files.readString(p, StandardCharsets.ISO_8859_1), ds.lrecl());
                    }
                }
            }
            Path appData = Path.of(props.seed().appData());
            if (ds.asciiSrc() != null) {
                Path p = appData.resolve("ASCII").resolve(ds.asciiSrc());
                log.info("{} <- {}", ds.name(), p);
                return Files.readString(p, StandardCharsets.ISO_8859_1).lines()
                        .map(l -> l.replace("\r", "")).toList();
            }
            Path p = appData.resolve("EBCDIC").resolve(ds.ebcdicSrc());
            log.info("{} <- {} (cp037)", ds.name(), p);
            return fixed(new String(Files.readAllBytes(p), CP037), ds.lrecl());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read seed dataset " + ds.name(), e);
        }
    }

    static List<String> fixed(String data, int lrecl) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i + lrecl <= data.length(); i += lrecl) {
            out.add(data.substring(i, i + lrecl));
        }
        return out;
    }

    /** Per-dataset record counts. */
    public static class LoadSummary extends java.util.LinkedHashMap<String, Integer> {
    }
}
