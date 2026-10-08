package com.carddemo.online.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtServiceTest {
    private static CardDemoProperties props(String secret) {
        return new CardDemoProperties("2022-07-06T10:00:00", new CardDemoProperties.Jwt(secret, Duration.ofMinutes(30),
                "carddemo-online"), null, null);
    }

    private static MockEnvironment env(String... profiles) {
        MockEnvironment e = new MockEnvironment();
        e.setActiveProfiles(profiles);
        return e;
    }

    @Test
    @DisplayName("ONL-SEC-03 COMMAREA replacement: blank CARDDEMO_JWT_SECRET fails start-up outside 'local'")
    void blankSecretFails() {
        assertThatThrownBy(() -> new JwtService(props(""), env())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CARDDEMO_JWT_SECRET");
        assertThatThrownBy(() -> new JwtService(props(null), env("redis"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ONL-SEC-03 COMMAREA replacement: CARDDEMO_JWT_SECRET shorter than 32 bytes fails start-up")
    void shortSecretFails() {
        assertThatThrownBy(() -> new JwtService(props("0123456789abcdef0123456789abcde"), env()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ONL-SEC-03 COMMAREA replacement: a 32-byte secret is shared, so any instance verifies any token")
    void sharedSecretVerifiesAcrossInstances() {
        String secret = "0123456789abcdef0123456789abcdef";
        String token = new JwtService(props(secret), env()).issue(new SessionUser("ADMIN001", "A", "Admin"));
        assertThat(new JwtService(props(secret), env()).verify(token)).get()
                .extracting(SessionUser::userId).isEqualTo("ADMIN001");
    }

    @Test
    @DisplayName("ONL-SEC-03 COMMAREA replacement: 'local' profile falls back to an ephemeral key")
    void localProfileUsesEphemeralKey() {
        JwtService svc = new JwtService(props(""), env("local"));
        String token = svc.issue(new SessionUser("USER0001", "U", "User"));
        assertThat(svc.verify(token)).isPresent();
        assertThat(new JwtService(props(""), env("local")).verify(token)).isEmpty();
    }
}
