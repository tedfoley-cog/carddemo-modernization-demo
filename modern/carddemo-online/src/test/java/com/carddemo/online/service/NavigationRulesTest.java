package com.carddemo.online.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.online.config.SessionUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** ONL-NAV-01 menus and ONL-LST-01 list selection edits. */
class NavigationRulesTest {
    private final MenuService menus = new MenuService();
    private static final SessionUser USER = new SessionUser("USER0001", "U", "u");
    private static final SessionUser ADMIN = new SessionUser("ADMIN001", "A", "a");

    @Test
    @DisplayName("ONL-NAV-01 COMEN02Y/COADM02Y: 11 main and 6 admin options (requirements text says 12/9 - see quirks)")
    void counts() {
        assertThat(menus.menuFor(USER)).hasSize(11);
        assertThat(menus.menuFor(ADMIN)).hasSize(6);
    }

    @ParameterizedTest(name = "[{index}] option {0} -> {1}/{2}")
    @DisplayName("ONL-NAV-01 COMEN01C PROCESS-ENTER-KEY: option dispatches to the menu-table program")
    @CsvSource({"1,COACTVWC,CAVW", "01,COACTVWC,CAVW", "2,COACTUPC,CAUP", "3,COCRDLIC,CCLI", "6,COTRN00C,CT00",
        "8,COTRN02C,CT02", "10,COBIL00C,CB00", " 6,COTRN00C,CT00"})
    void dispatch(String option, String program, String tran) {
        var s = menus.select(USER, option);
        assertThat(s.program()).isEqualTo(program);
        assertThat(s.transaction()).isEqualTo(tran);
    }

    @ParameterizedTest(name = "[{index}] option ''{0}''")
    @DisplayName("ONL-NAV-01 COMEN01C PROCESS-ENTER-KEY: invalid option number")
    @CsvSource(value = {"0", "12", "99", "AB", "''"}, emptyValue = "")
    void invalid(String option) {
        assertThatThrownBy(() -> menus.select(USER, option)).hasMessage("Please enter a valid option number...");
    }

    @Test
    @DisplayName("ONL-NAV-01 COMEN01C PROCESS-ENTER-KEY: uninstalled program message names the option")
    void notInstalled() {
        assertThatThrownBy(() -> menus.select(USER, "11"))
                .hasMessage("This option Pending Authorization View is not installed...");
        assertThatThrownBy(() -> menus.select(USER, "9")).hasMessage("This option Transaction Reports is not installed...");
    }

    @Test
    @DisplayName("ONL-NAV-01 COADM01C PGMIDERR-ERR-PARA: admin uninstalled option (legacy runtime abends APCT instead)")
    void adminNotInstalled() {
        assertThatThrownBy(() -> menus.select(ADMIN, "5")).hasMessage("This option is not installed ...");
        assertThat(menus.select(ADMIN, "1").program()).isEqualTo("COUSR00C");
        assertThatThrownBy(() -> menus.select(ADMIN, "7")).hasMessage("Please enter a valid option number...");
    }

    @Test
    @DisplayName("ONL-LST-01 COTRN00C PROCESS-ENTER-KEY: only S/s is a valid selection")
    void tranSelection() {
        TransactionQueryService.editSelection("S");
        TransactionQueryService.editSelection("s");
        TransactionQueryService.editSelection("");
        assertThatThrownBy(() -> TransactionQueryService.editSelection("X"))
                .hasMessage("Invalid selection. Valid value is S");
    }

    @Test
    @DisplayName("ONL-LST-01 COUSR00C PROCESS-ENTER-KEY: U/u update, D/d delete, anything else invalid")
    void userSelection() {
        assertThat(UserAdminService.editSelection("u")).isEqualTo("COUSR02C");
        assertThat(UserAdminService.editSelection("D")).isEqualTo("COUSR03C");
        assertThatThrownBy(() -> UserAdminService.editSelection("S"))
                .hasMessage("Invalid selection. Valid values are U and D");
    }
}
