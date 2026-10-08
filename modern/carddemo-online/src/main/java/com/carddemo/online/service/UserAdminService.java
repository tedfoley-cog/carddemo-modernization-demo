package com.carddemo.online.service;

import com.carddemo.domain.model.UserSecurity;
import com.carddemo.online.repo.UserSecurityRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COUSR00C list (CU00), COUSR01C add (CU01), COUSR02C update (CU02), COUSR03C delete (CU03). */
@Service
public class UserAdminService {
    public static final int PAGE_SIZE = 10;
    private final UserSecurityRepository users;
    private final PasswordEncoder encoder;

    public UserAdminService(UserSecurityRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public record UserRow(String userId, String firstName, String lastName, String userType) {
    }

    public record UserPage(int page, List<UserRow> rows, boolean hasNext) {
    }

    /** Password is never echoed back (the BMS map displayed it; see legacy quirks / deviations). */
    public record UserDetail(String userId, String firstName, String lastName, String userType, String message) {
    }

    public record UserRequest(String userId, String firstName, String lastName, String password, String userType) {
    }

    public record Result(String message) {
    }

    @Transactional(readOnly = true)
    public UserPage list(String fromId, String after, String before, int page) {
        if (before != null) {
            if (page <= 1) {
                throw new LegacyRuleException("You are already at the top of the page...", null,
                        "COUSR00C PROCESS-PF7-KEY");
            }
            List<UserSecurity> prev = new ArrayList<>(users.findByUserIdLessThanOrderByUserIdDesc(before,
                    PageRequest.of(0, PAGE_SIZE)));
            Collections.reverse(prev);
            return new UserPage(page - 1, rows(prev), true);
        }
        List<UserSecurity> found = after != null
                ? users.findByUserIdGreaterThanOrderByUserIdAsc(after, PageRequest.of(0, PAGE_SIZE + 1))
                : users.findByUserIdGreaterThanEqualOrderByUserIdAsc(fromId == null ? "" : fromId.trim().toUpperCase(),
                        PageRequest.of(0, PAGE_SIZE + 1));
        if (after != null && found.isEmpty()) {
            throw new LegacyRuleException("You are already at the bottom of the page...", null,
                    "COUSR00C PROCESS-PF8-KEY");
        }
        return new UserPage(after != null ? page + 1 : 1, rows(found.subList(0, Math.min(PAGE_SIZE, found.size()))),
                found.size() > PAGE_SIZE);
    }

    /** COUSR00C PROCESS-ENTER-KEY selection: U/u = update, D/d = delete. */
    public static String editSelection(String sel) {
        return switch (sel == null ? "" : sel.trim()) {
            case "U", "u" -> "COUSR02C";
            case "D", "d" -> "COUSR03C";
            default -> throw new LegacyRuleException("Invalid selection. Valid values are U and D", "selection",
                    "COUSR00C PROCESS-ENTER-KEY");
        };
    }

    /** COUSR01C PROCESS-ENTER-KEY + WRITE-USER-SEC-FILE. */
    @Transactional
    public Result add(UserRequest r) {
        String p = "COUSR01C PROCESS-ENTER-KEY";
        required(r.firstName(), "First Name", "firstName", p);
        required(r.lastName(), "Last Name", "lastName", p);
        required(r.userId(), "User ID", "userId", p);
        required(r.password(), "Password", "password", p);
        required(r.userType(), "User Type", "userType", p);
        String id = r.userId().trim();
        if (users.existsById(id)) {
            throw new LegacyRuleException("User ID already exist...", "userId",
                    "COUSR01C WRITE-USER-SEC-FILE", 409);
        }
        UserSecurity u = new UserSecurity();
        u.setUserId(id);
        apply(u, r);
        users.save(u);
        return new Result("User " + firstWord(id) + " has been added ...");
    }

    /** COUSR02C / COUSR03C READ-USER-SEC-FILE. */
    @Transactional(readOnly = true)
    public UserDetail fetch(String userId, boolean forDelete) {
        String program = forDelete ? "COUSR03C" : "COUSR02C";
        UserSecurity u = read(userId, program);
        return new UserDetail(u.getUserId(), u.getFirstName(), u.getLastName(), u.getUserType(),
                forDelete ? "Press PF5 key to delete this user ..." : "Press PF5 key to save your updates ...");
    }

    /** COUSR02C UPDATE-USER-INFO (PF5). */
    @Transactional
    public Result update(UserRequest r) {
        String p = "COUSR02C UPDATE-USER-INFO";
        required(r.userId(), "User ID", "userId", p);
        required(r.firstName(), "First Name", "firstName", p);
        required(r.lastName(), "Last Name", "lastName", p);
        required(r.password(), "Password", "password", p);
        required(r.userType(), "User Type", "userType", p);
        UserSecurity u = read(r.userId(), "COUSR02C");
        boolean modified = !r.firstName().equals(u.getFirstName()) || !r.lastName().equals(u.getLastName())
                || !encoder.matches(r.password(), u.getPasswordHash()) || !r.userType().equals(u.getUserType());
        if (!modified) {
            throw new LegacyRuleException("Please modify to update ...", null, "COUSR02C UPDATE-USER-INFO");
        }
        apply(u, r);
        return new Result("User " + firstWord(u.getUserId()) + " has been updated ...");
    }

    /** COUSR03C DELETE-USER-INFO (PF5). */
    @Transactional
    public Result delete(String userId) {
        UserSecurity u = read(userId, "COUSR03C");
        users.delete(u);
        return new Result("User " + firstWord(u.getUserId()) + " has been deleted ...");
    }

    private UserSecurity read(String userId, String program) {
        required(userId, "User ID", "userId", program + " PROCESS-ENTER-KEY");
        return users.findById(userId.trim()).orElseThrow(() -> LegacyRuleException.notFound("User ID NOT found...",
                "userId", program + " READ-USER-SEC-FILE"));
    }

    /** QUIRK-USR-01: the password is stored as typed while sign-on upper-cases the input. */
    private void apply(UserSecurity u, UserRequest r) {
        u.setFirstName(r.firstName());
        u.setLastName(r.lastName());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setUserType(r.userType());
    }

    private static void required(String v, String name, String field, String para) {
        if (v == null || v.isBlank()) {
            throw new LegacyRuleException(name + " can NOT be empty...", field, para);
        }
    }

    private static String firstWord(String s) {
        int i = s.indexOf(' ');
        return i < 0 ? s : s.substring(0, i);
    }

    private static List<UserRow> rows(List<UserSecurity> list) {
        return list.stream().map(u -> new UserRow(u.getUserId(), u.getFirstName(), u.getLastName(), u.getUserType()))
                .toList();
    }
}
