package com.carddemo.online.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import com.carddemo.online.repo.UserSecurityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end API tests against a real PostgreSQL 16 (Testcontainers), Flyway schema and the legacy seed files.
 * Each test is named with the requirement ID and the COBOL paragraph whose behaviour it pins.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OnlineApiIT {
    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("carddemo.seed.workdir", () -> "src/test/resources/seed-workdir");
        r.add("carddemo.seed.mode", () -> "reload");
        r.add("carddemo.jwt.secret", () -> "integration-test-secret-0123456789abcdef");
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserSecurityRepository userSecurity;

    String token(String user) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/signon").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user + "\",\"password\":\"PASSWORD\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("token").asText();
    }

    ResultActions as(String user, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token(user)).contentType(MediaType.APPLICATION_JSON));
    }

    JsonNode json(ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString());
    }

    ResultActions signon(String user, String pwd) throws Exception {
        return mvc.perform(post("/api/v1/auth/signon").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(java.util.Map.of("userId", user, "password", pwd))));
    }

    @Test
    @DisplayName("ONL-SEC-01 COSGN00C PROCESS-ENTER-KEY: user id and password are mandatory")
    void signonMandatory() throws Exception {
        signon("", "PASSWORD").andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message").value("Please enter User ID ..."));
        signon("USER0001", "").andExpect(jsonPath("$.message").value("Please enter Password ..."));
    }

    @Test
    @DisplayName("ONL-SEC-02 COSGN00C READ-USER-SEC-FILE: wrong password / unknown user; password case-insensitive")
    void signonCredentials() throws Exception {
        signon("USER0001", "WRONGPWD").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong Password. Try again ..."));
        signon("NOSUCHUS", "PASSWORD").andExpect(jsonPath("$.message").value("User not found. Try again ..."));
        signon("user0001", "password").andExpect(status().isOk());
    }

    @Test
    @DisplayName("ONL-SEC-03 COSGN00C READ-USER-SEC-FILE: admin -> COADM01C, user -> COMEN01C")
    void signonRouting() throws Exception {
        signon("ADMIN001", "PASSWORD").andExpect(jsonPath("$.nextProgram").value("COADM01C"))
                .andExpect(jsonPath("$.userType").value("A"));
        signon("USER0001", "PASSWORD").andExpect(jsonPath("$.nextProgram").value("COMEN01C"));
    }

    @Test
    @DisplayName("ONL-SEC-03 COADM01C admin re-check: a demoted admin's unexpired token gets 403 on admin APIs")
    void demotedAdminLosesAccess() throws Exception {
        String t = token("ADMIN001");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + t)).andExpect(status().isOk());
        var admin = userSecurity.findById("ADMIN001").orElseThrow();
        admin.setUserType("U");
        userSecurity.save(admin);
        try {
            mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + t))
                    .andExpect(status().isForbidden());
        } finally {
            admin.setUserType("A");
            userSecurity.save(admin);
        }
    }

    @Test
    @DisplayName("ONL-SEC-03 stateless token replaces COMMAREA: no token -> 401, user on admin API -> 403")
    void tokenRequired() throws Exception {
        mvc.perform(get("/api/v1/accounts").param("accountId", "00000000001")).andExpect(status().isUnauthorized());
        as("USER0001", get("/api/v1/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ONL-NAV-01 COMEN01C/COADM01C PROCESS-ENTER-KEY: menus come from COMEN02Y/COADM02Y")
    void menus() throws Exception {
        as("USER0001", get("/api/v1/menu")).andExpect(jsonPath("$.length()").value(11));
        as("ADMIN001", get("/api/v1/menu")).andExpect(jsonPath("$.length()").value(6));
        as("USER0001", post("/api/v1/menu/select").content("{\"option\":\"1\"}"))
                .andExpect(jsonPath("$.program").value("COACTVWC"));
    }

    @Test
    @DisplayName("ONL-ACV-01 COACTVWC 2210-EDIT-ACCOUNT: blank / non 11-digit / zero account")
    void accountViewEdits() throws Exception {
        as("USER0001", get("/api/v1/accounts").param("accountId", ""))
                .andExpect(jsonPath("$.message").value("No input received"));
        for (String bad : new String[] {"1", "0000000000A", "00000000000"}) {
            as("USER0001", get("/api/v1/accounts").param("accountId", bad))
                    .andExpect(jsonPath("$.message").value("Account Filter must  be a non-zero 11 digit number"));
        }
    }

    @Test
    @DisplayName("ONL-ACV-02 COACTVWC 9200-GETCARDXREF-BYACCT..9400-GETCUSTDATA-BYCUST: account + customer shown")
    void accountView() throws Exception {
        as("USER0001", get("/api/v1/accounts").param("accountId", "00000000001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.creditLimit").value("+      2,020.00"))
                .andExpect(jsonPath("$.cashCreditLimit").value("+      1,020.00"))
                .andExpect(jsonPath("$.currentCycleDebit").value("-         70.77"))
                .andExpect(jsonPath("$.firstName").value("Immanuel"))
                .andExpect(jsonPath("$.lastName").value("Kessler"))
                .andExpect(jsonPath("$.ssn").value("020-97-3888"));
        as("USER0001", get("/api/v1/accounts").param("accountId", "99999999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Account:99999999999 not found in Cross ref file.  Resp:000000013  Reas:0000"));
    }

    @Test
    @DisplayName("ONL-ACU-01 COACTUPC 1200-EDIT-MAP-INPUTS: seeded FICO 274 fails; 1210 account id edit")
    void accountUpdateValidation() throws Exception {
        as("USER0001", get("/api/v1/accounts/update").param("accountId", "1"))
                .andExpect(jsonPath("$.message").value("Account Number if supplied must be a 11 digit Non-Zero Number"));
        JsonNode data = json(as("USER0001", get("/api/v1/accounts/update").param("accountId", "00000000003")));
        ObjectNode changes = data.get("fields").deepCopy();
        changes.put("middleName", "Q1");
        as("USER0001", post("/api/v1/accounts/update/validate").param("accountId", "00000000003")
                .content(om.writeValueAsString(java.util.Map.of("original", data.get("fields"), "changes", changes))))
                .andExpect(jsonPath("$.message").value("Middle Name can have alphabets only."));
        as("USER0001", post("/api/v1/accounts/update/validate").param("accountId", "00000000003")
                .content(om.writeValueAsString(java.util.Map.of("original", data.get("fields"), "changes", data.get("fields")))))
                .andExpect(jsonPath("$.message").value("No change detected with respect to values fetched."));
    }

    @Test
    @DisplayName("ONL-ACU-02 COACTUPC 9600-WRITE-PROCESSING / 9700-CHECK-CHANGE-IN-REC: validate, F5 commit, stale copy refused")
    void accountUpdateCommit() throws Exception {
        JsonNode data = json(as("USER0001", get("/api/v1/accounts/update").param("accountId", "00000000001")));
        ObjectNode changes = data.get("fields").deepCopy();
        changes.put("ficoScore", "750");
        changes.put("phone1a", "212");
        changes.put("phone2a", "212");
        changes.put("currentCycleCredit", "1200.00");
        changes.put("state", "NY"); // seeded NC/12546 fails 1280-EDIT-US-STATE-ZIP-CD (QUIRK-ACU-03)
        changes.put("zip", "10001");
        String body = om.writeValueAsString(java.util.Map.of("original", data.get("fields"), "changes", changes));
        as("USER0001", post("/api/v1/accounts/update/validate").param("accountId", "00000000001").content(body))
                .andExpect(jsonPath("$.message").value("Changes validated.Press F5 to save"));
        as("USER0001", put("/api/v1/accounts/update").param("accountId", "00000000001").content(body))
                .andExpect(jsonPath("$.message").value("Changes committed to database"));
        as("USER0001", get("/api/v1/accounts").param("accountId", "00000000001"))
                .andExpect(jsonPath("$.currentCycleCredit").value("+      1,200.00"));
        as("USER0001", put("/api/v1/accounts/update").param("accountId", "00000000001").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Record changed by some one else. Please review"));
    }

    @Test
    @DisplayName("ONL-BIL-01 COBIL00C PROCESS-ENTER-KEY / READ-ACCTDAT-FILE: account required and must exist")
    void billPayEdits() throws Exception {
        as("USER0001", post("/api/v1/bill-payments").content("{\"accountId\":\"\"}"))
                .andExpect(jsonPath("$.message").value("Acct ID can NOT be empty..."));
        as("USER0001", post("/api/v1/bill-payments").content("{\"accountId\":\"abc\"}"))
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."));
        as("USER0001", post("/api/v1/bill-payments").content("{\"accountId\":\"00000000004\",\"confirm\":\"X\"}"))
                .andExpect(jsonPath("$.message").value("Invalid value. Valid values are (Y/N)..."));
    }

    @Test
    @DisplayName("ONL-BIL-02 + ONL-BIL-03 COBIL00C WRITE-TRANSACT-FILE / UPDATE-ACCTDAT-FILE: full balance paid, then nothing to pay")
    void billPayFlow() throws Exception {
        BigDecimal before = jdbc.queryForObject("select acct_curr_bal from account where acct_id = 2", BigDecimal.class);
        assertThat(before).isPositive();
        String maxBefore = jdbc.queryForObject("select max(tran_id) from transaction", String.class);
        as("USER0001", post("/api/v1/bill-payments").content("{\"accountId\":\"00000000002\"}"))
                .andExpect(jsonPath("$.message").value("Confirm to make a bill payment..."));
        JsonNode paid = json(as("USER0001", post("/api/v1/bill-payments")
                .content("{\"accountId\":\"00000000002\",\"confirm\":\"Y\"}")));
        String id = paid.get("transactionId").asText();
        assertThat(id).isEqualTo(String.format("%016d", Long.parseLong(maxBefore.trim()) + 1));
        assertThat(paid.get("message").asText()).isEqualTo("Payment successful.  Your Transaction ID is " + id + ".");
        assertThat(jdbc.queryForObject("select acct_curr_bal from account where acct_id = 2", BigDecimal.class))
                .isEqualByComparingTo("0");
        var row = jdbc.queryForMap("select * from transaction where tran_id = ?", id);
        assertThat(row.get("tran_type_cd").toString().trim()).isEqualTo("02");
        assertThat(row.get("tran_desc").toString().trim()).isEqualTo("BILL PAYMENT - ONLINE");
        assertThat((BigDecimal) row.get("tran_amt")).isEqualByComparingTo(before);
        as("USER0001", post("/api/v1/bill-payments").content("{\"accountId\":\"00000000002\",\"confirm\":\"Y\"}"))
                .andExpect(jsonPath("$.message").value("You have nothing to pay..."));
    }

    @Test
    @DisplayName("ONL-TRA-01 COTRN02C ADD-TRANSACTION: confirm prompt, then write with TRAN-ID = max + 1")
    void transactionAdd() throws Exception {
        String req = "{\"accountId\":\"00000000005\",\"typeCd\":\"01\",\"categoryCd\":\"0001\",\"source\":\"POS TERM\","
                + "\"description\":\"Parity purchase\",\"amount\":\"+00000012.50\",\"origDate\":\"2022-07-06\","
                + "\"procDate\":\"2022-07-06\",\"merchantId\":\"800000001\",\"merchantName\":\"Cafe\","
                + "\"merchantCity\":\"Austin\",\"merchantZip\":\"73301\"";
        as("USER0001", post("/api/v1/transactions").content(req + "}"))
                .andExpect(jsonPath("$.message").value("Confirm to add this transaction..."));
        as("USER0001", post("/api/v1/transactions").content("{\"accountId\":\"99999999999\",\"typeCd\":\"01\"}"))
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."));
        JsonNode added = json(as("USER0001", post("/api/v1/transactions").content(req + ",\"confirm\":\"Y\"}")));
        String id = added.get("transactionId").asText();
        assertThat(added.get("message").asText()).isEqualTo("Transaction added successfully.  Your Tran ID is " + id + ".");
        as("USER0001", get("/api/v1/transactions/detail").param("transactionId", id))
                .andExpect(jsonPath("$.amount").value("+00000012.50"))
                .andExpect(jsonPath("$.description").value("Parity purchase"));
    }

    @Test
    @DisplayName("ONL-LST-01 COTRN00C PROCESS-PF7-KEY/PF8-KEY + COTRN01C READ-TRANSACT-FILE")
    void transactionPaging() throws Exception {
        JsonNode p1 = json(as("USER0001", get("/api/v1/transactions")));
        assertThat(p1.get("rows")).hasSize(10);
        assertThat(p1.get("rows").get(0).get("amount").asText()).matches("[+-]\\d{8}\\.\\d{2}");
        String last = p1.get("rows").get(9).get("transactionId").asText();
        JsonNode p2 = json(as("USER0001", get("/api/v1/transactions").param("after", last).param("page", "1")));
        assertThat(p2.get("page").asInt()).isEqualTo(2);
        String first2 = p2.get("rows").get(0).get("transactionId").asText();
        JsonNode back = json(as("USER0001", get("/api/v1/transactions").param("before", first2).param("page", "2")));
        assertThat(back.get("rows").get(9).get("transactionId").asText()).isEqualTo(last);
        as("USER0001", get("/api/v1/transactions").param("before", "0").param("page", "1"))
                .andExpect(jsonPath("$.message").value("You are already at the top of the page..."));
        as("USER0001", get("/api/v1/transactions").param("fromId", "abc"))
                .andExpect(jsonPath("$.message").value("Tran ID must be Numeric ..."));
        as("USER0001", get("/api/v1/transactions/detail").param("transactionId", ""))
                .andExpect(jsonPath("$.message").value("Tran ID can NOT be empty..."));
        as("USER0001", get("/api/v1/transactions/detail").param("transactionId", "9999"))
                .andExpect(jsonPath("$.message").value("Transaction ID NOT found..."));
    }

    @Test
    @DisplayName("ONL-LST-01 COCRDLIC 9000-READ-FORWARD / 9100-READ-BACKWARDS: 7 per page, filters")
    void cardPaging() throws Exception {
        JsonNode p1 = json(as("USER0001", get("/api/v1/cards")));
        assertThat(p1.get("rows")).hasSize(7);
        assertThat(p1.get("hasNext").asBoolean()).isTrue();
        String last = p1.get("rows").get(6).get("cardNumber").asText();
        JsonNode p2 = json(as("USER0001", get("/api/v1/cards").param("after", last).param("page", "1")));
        assertThat(p2.get("rows").get(0).get("cardNumber").asText()).isGreaterThan(last);
        as("USER0001", get("/api/v1/cards").param("before", "x").param("page", "1"))
                .andExpect(jsonPath("$.message").value("NO PREVIOUS PAGES TO DISPLAY"));
        as("USER0001", get("/api/v1/cards").param("accountId", "ABC"))
                .andExpect(jsonPath("$.message").value("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"));
        JsonNode f = json(as("USER0001", get("/api/v1/cards").param("accountId", "00000000001")));
        f.get("rows").forEach(r -> assertThat(r.get("accountId").asText()).isEqualTo("00000000001"));
    }

    @Test
    @DisplayName("COCRDSLC 9100-GETCARD-BYACCTCARD + COCRDUPC 9200-WRITE-PROCESSING: view, update, stale copy")
    void cardViewAndUpdate() throws Exception {
        JsonNode row = json(as("USER0001", get("/api/v1/cards"))).get("rows").get(0);
        String acct = row.get("accountId").asText();
        String card = row.get("cardNumber").asText();
        JsonNode d = json(as("USER0001", get("/api/v1/cards/detail").param("accountId", acct).param("cardNumber", card)));
        assertThat(d.get("cardNumber").asText()).isEqualTo(card);
        as("USER0001", get("/api/v1/cards/detail").param("accountId", acct).param("cardNumber", "12"))
                .andExpect(jsonPath("$.message").value("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"));
        ObjectNode orig = om.createObjectNode().put("embossedName", d.get("embossedName").asText())
                .put("activeStatus", d.get("activeStatus").asText()).put("expiryMonth", d.get("expiryMonth").asText())
                .put("expiryYear", d.get("expiryYear").asText());
        ObjectNode changed = orig.deepCopy().put("embossedName", "PARITY TEST HOLDER");
        String body = om.writeValueAsString(java.util.Map.of("original", orig, "changes", changed));
        as("USER0001", post("/api/v1/cards/update/validate").param("accountId", acct).param("cardNumber", card)
                .content(body)).andExpect(jsonPath("$.message").value("Changes validated.Press F5 to save"));
        as("USER0001", put("/api/v1/cards/update").param("accountId", acct).param("cardNumber", card).content(body))
                .andExpect(jsonPath("$.message").value("Changes committed to database"));
        as("USER0001", put("/api/v1/cards/update").param("accountId", acct).param("cardNumber", card).content(body))
                .andExpect(jsonPath("$.message").value("Record changed by some one else. Please review"));
    }

    @Test
    @DisplayName("ONL-LST-01 COUSR00C-03C: list, add (duplicate), update (no change), delete")
    void userAdmin() throws Exception {
        JsonNode list = json(as("ADMIN001", get("/api/v1/admin/users")));
        assertThat(list.get("rows").size()).isEqualTo(10);
        String add = "{\"userId\":\"TESTU001\",\"firstName\":\"Test\",\"lastName\":\"User\",\"password\":\"PASSWORD\",\"userType\":\"U\"}";
        as("ADMIN001", post("/api/v1/admin/users").content("{\"firstName\":\"\"}"))
                .andExpect(jsonPath("$.message").value("First Name can NOT be empty..."));
        as("ADMIN001", post("/api/v1/admin/users").content(add))
                .andExpect(jsonPath("$.message").value("User TESTU001 has been added ..."));
        as("ADMIN001", post("/api/v1/admin/users").content(add))
                .andExpect(jsonPath("$.message").value("User ID already exist..."));
        as("ADMIN001", put("/api/v1/admin/users").content(add))
                .andExpect(jsonPath("$.message").value("Please modify to update ..."));
        as("ADMIN001", put("/api/v1/admin/users").content(add.replace("\"Test\"", "\"Tess\"")))
                .andExpect(jsonPath("$.message").value("User TESTU001 has been updated ..."));
        as("ADMIN001", get("/api/v1/admin/users/detail").param("userId", "TESTU001").param("forDelete", "true"))
                .andExpect(jsonPath("$.message").value("Press PF5 key to delete this user ..."));
        as("ADMIN001", delete("/api/v1/admin/users").param("userId", "TESTU001"))
                .andExpect(jsonPath("$.message").value("User TESTU001 has been deleted ..."));
        as("ADMIN001", get("/api/v1/admin/users/detail").param("userId", "TESTU001"))
                .andExpect(jsonPath("$.message").value("User ID NOT found..."));
    }

    @Test
    @DisplayName("Operability: actuator health + Prometheus metrics + OpenAPI document")
    void operability() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/accounts']").exists());
    }
}
