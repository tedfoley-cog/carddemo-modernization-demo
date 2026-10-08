package com.carddemo.batch;

import com.carddemo.batch.config.CardDemoBatchProperties;
import com.carddemo.batch.config.CardDemoBatchProperties.LegacyFixes;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TestProperties {

    public static final LocalDateTime BUSINESS_TS = LocalDateTime.of(2022, 7, 6, 10, 0);

    private TestProperties() {
    }

    public static CardDemoBatchProperties legacy() {
        return with(new LegacyFixes(false, false, false, false, false, false, false));
    }

    public static CardDemoBatchProperties with(LegacyFixes fixes) {
        return new CardDemoBatchProperties("nightlyStream", BUSINESS_TS, "2022071800", LocalDate.of(2022, 1, 1),
                LocalDate.of(2022, 7, 6), null, Path.of("target/test-out"), true, true, fixes);
    }
}
