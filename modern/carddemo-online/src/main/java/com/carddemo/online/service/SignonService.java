package com.carddemo.online.service;

import com.carddemo.online.config.CardDemoProperties;
import com.carddemo.online.config.JwtService;
import com.carddemo.online.config.SessionUser;
import com.carddemo.online.domain.UserSecurity;
import com.carddemo.online.repo.UserSecurityRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** COSGN00C — sign-on (TRANID CC00). */
@Service
public class SignonService {
    public static final String MSG_SIGNOFF = "Thank you for using CardDemo application...";

    private final UserSecurityRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CardDemoProperties props;

    public SignonService(UserSecurityRepository users, PasswordEncoder encoder, JwtService jwt, CardDemoProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
    }

    public record SignonResult(String token, long expiresIn, String userId, String userType, String displayName,
                               String nextProgram, String nextTransaction) {
    }

    /** COSGN00C PROCESS-ENTER-KEY + READ-USER-SEC-FILE. */
    public SignonResult signon(String userIdIn, String passwordIn) {
        // ONL-SEC-01  COSGN00C PROCESS-ENTER-KEY: user id checked before password
        if (isBlank(userIdIn)) {
            throw new LegacyRuleException("Please enter User ID ...", "userId", "COSGN00C PROCESS-ENTER-KEY", 422);
        }
        if (isBlank(passwordIn)) {
            throw new LegacyRuleException("Please enter Password ...", "password", "COSGN00C PROCESS-ENTER-KEY", 422);
        }
        // COSGN00C: FUNCTION UPPER-CASE on both fields before the USRSEC read / compare
        String userId = upper8(userIdIn);
        String password = props.quirks().caseInsensitivePassword() ? upper8(passwordIn) : trim8(passwordIn);

        Optional<UserSecurity> found;
        try {
            found = users.findById(userId);
        } catch (DataAccessException e) {
            throw new LegacyRuleException("Unable to verify the User ...", "userId", "COSGN00C READ-USER-SEC-FILE", 503);
        }
        // ONL-SEC-02  COSGN00C READ-USER-SEC-FILE
        UserSecurity u = found.orElseThrow(() -> new LegacyRuleException(
                "User not found. Try again ...", "userId", "COSGN00C READ-USER-SEC-FILE", 401));
        if (!encoder.matches(password, u.getPasswordHash())) {
            throw new LegacyRuleException("Wrong Password. Try again ...", "password", "COSGN00C READ-USER-SEC-FILE", 401);
        }
        // ONL-SEC-03  admin -> COADM01C (CA00), user -> COMEN01C (CM00)
        SessionUser session = new SessionUser(u.getUserId(), u.getUserType(),
                (u.getFirstName() + " " + u.getLastName()).trim());
        return new SignonResult(jwt.issue(session), jwt.ttlSeconds(), u.getUserId(), u.getUserType(),
                session.displayName(), u.isAdmin() ? "COADM01C" : "COMEN01C", u.isAdmin() ? "CA00" : "CM00");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim8(String s) {
        String t = s.strip();
        return t.length() > 8 ? t.substring(0, 8) : t;
    }

    private static String upper8(String s) {
        return trim8(s).toUpperCase(Locale.ROOT);
    }
}
