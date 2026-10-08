package com.carddemo.online.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All runtime configuration; every value is overridable through environment variables
 * (CARDDEMO_CLOCK, CARDDEMO_JWT_SECRET, CARDDEMO_SEED_DIR, ...).
 *
 * @param clock    business clock ISO local date-time, or "system" for wall clock (legacy region --clock)
 * @param jwt      session token settings (replaces CICS sign-on + COMMAREA)
 * @param seed     fixed-width seed data loader
 * @param quirks   switches for documented legacy defects; default true = behave like the COBOL
 */
@ConfigurationProperties(prefix = "carddemo")
public record CardDemoProperties(String clock, Jwt jwt, Seed seed, Quirks quirks) {

    public record Jwt(String secret, Duration ttl, String issuer) {
    }

    /**
     * @param workdir  legacy runtime work directory (batch.py --workdir), uses out/*.dat then ds/*.PS
     * @param appData  CardDemo app/data directory (ASCII + EBCDIC) used when no workdir file exists
     * @param mode     none | if-empty | reload
     */
    public record Seed(String workdir, String appData, String mode) {
    }

    /**
     * @param blankPhoneAccepted     COACTUPC 1260: blank phone is accepted (legacy) instead of "must be supplied"
     * @param caseInsensitivePassword COSGN00C upper-cases the password before comparing
     * @param transactionIdMaxPlusOne COTRN02C/COBIL00C: next TRAN-ID = highest key + 1 (no sequence, race-prone)
     */
    public record Quirks(boolean blankPhoneAccepted, boolean caseInsensitivePassword, boolean transactionIdMaxPlusOne) {
    }
}
