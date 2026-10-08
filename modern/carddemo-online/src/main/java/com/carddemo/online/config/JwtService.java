package com.carddemo.online.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the signed session token that replaces the CICS sign-on COMMAREA fields
 * CDEMO-USER-ID / CDEMO-USER-TYPE. Stateless, so any Cloud Run instance can serve any request.
 */
@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private final SecretKey key;
    private final CardDemoProperties.Jwt cfg;

    static final int MIN_SECRET_BYTES = 32;

    /**
     * A per-instance random key would make tokens fail across Cloud Run instances, so a missing or weak
     * CARDDEMO_JWT_SECRET stops start-up. Only the explicit {@code local} profile falls back to an ephemeral key.
     */
    public JwtService(CardDemoProperties props, Environment env) {
        this.cfg = props.jwt();
        String secret = cfg.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            if (!env.acceptsProfiles(Profiles.of("local"))) {
                throw new IllegalStateException("CARDDEMO_JWT_SECRET must be set to at least " + MIN_SECRET_BYTES
                        + " bytes (or run with the 'local' profile for an ephemeral development key)");
            }
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            secret = Base64.getEncoder().encodeToString(random);
            log.warn("CARDDEMO_JWT_SECRET missing or shorter than {} bytes: using an ephemeral key ('local' profile)",
                    MIN_SECRET_BYTES);
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(SessionUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(cfg.issuer())
                .subject(user.userId())
                .claim("typ", user.userType())
                .claim("name", user.displayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(cfg.ttl())))
                .signWith(key)
                .compact();
    }

    public Optional<SessionUser> verify(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(cfg.issuer()).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new SessionUser(c.getSubject(), c.get("typ", String.class), c.get("name", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long ttlSeconds() {
        return cfg.ttl().toSeconds();
    }
}
