package com.carddemo.online.service;

import com.carddemo.online.config.SessionUser;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** COMEN01C (CM00) main menu and COADM01C (CA00) admin menu, data-driven from COMEN02Y / COADM02Y. */
@Service
public class MenuService {

    public record MenuOption(int number, String name, String program, String transaction, String userType,
                             boolean installed) {
    }

    public record Selection(String program, String transaction) {
    }

    /** COMEN02Y CARDDEMO-MAIN-MENU-OPTIONS (CDEMO-MENU-OPT-COUNT = 11). */
    public static final List<MenuOption> MAIN = List.of(
            new MenuOption(1, "Account View", "COACTVWC", "CAVW", "U", true),
            new MenuOption(2, "Account Update", "COACTUPC", "CAUP", "U", true),
            new MenuOption(3, "Credit Card List", "COCRDLIC", "CCLI", "U", true),
            new MenuOption(4, "Credit Card View", "COCRDSLC", "CCDL", "U", true),
            new MenuOption(5, "Credit Card Update", "COCRDUPC", "CCUP", "U", true),
            new MenuOption(6, "Transaction List", "COTRN00C", "CT00", "U", true),
            new MenuOption(7, "Transaction View", "COTRN01C", "CT01", "U", true),
            new MenuOption(8, "Transaction Add", "COTRN02C", "CT02", "U", true),
            new MenuOption(9, "Transaction Reports", "CORPT00C", "CR00", "U", false),
            new MenuOption(10, "Bill Payment", "COBIL00C", "CB00", "U", true),
            new MenuOption(11, "Pending Authorization View", "COPAUS0C", "CPVS", "U", false));

    /** COADM02Y CARDDEMO-ADMIN-MENU-OPTIONS (CDEMO-ADMIN-OPT-COUNT = 6). */
    public static final List<MenuOption> ADMIN = List.of(
            new MenuOption(1, "User List (Security)", "COUSR00C", "CU00", "A", true),
            new MenuOption(2, "User Add (Security)", "COUSR01C", "CU01", "A", true),
            new MenuOption(3, "User Update (Security)", "COUSR02C", "CU02", "A", true),
            new MenuOption(4, "User Delete (Security)", "COUSR03C", "CU03", "A", true),
            new MenuOption(5, "Transaction Type List/Update (Db2)", "COTRTLIC", "CTLI", "A", false),
            new MenuOption(6, "Transaction Type Maintenance (Db2)", "COTRTUPC", "CTTU", "A", false));

    /** Programs reached through the INQUIRE PROGRAM check (COMEN01C) rather than a plain XCTL. */
    private static final Set<String> INQUIRED = Set.of("COPAUS0C");

    public List<MenuOption> menuFor(SessionUser user) {
        return user.isAdmin() ? ADMIN : MAIN;
    }

    /** COMEN01C / COADM01C PROCESS-ENTER-KEY. */
    public Selection select(SessionUser user, String optionText) {
        boolean admin = user.isAdmin();
        String pgm = admin ? "COADM01C" : "COMEN01C";
        List<MenuOption> table = admin ? ADMIN : MAIN;
        int option = parseOption(optionText);
        if (option <= 0 || option > table.size()) {
            throw new LegacyRuleException("Please enter a valid option number...", "option", pgm + " PROCESS-ENTER-KEY");
        }
        MenuOption opt = table.get(option - 1);
        if (!admin && "A".equals(opt.userType())) {
            throw new LegacyRuleException("No access - Admin Only option... ", "option", pgm + " PROCESS-ENTER-KEY", 403);
        }
        if (!opt.installed()) {
            if (admin) {
                // COADM01C PGMIDERR-ERR-PARA: option name is commented out of the STRING
                throw new LegacyRuleException("This option is not installed ...", "option", "COADM01C PGMIDERR-ERR-PARA", 501);
            }
            String para = INQUIRED.contains(opt.program()) ? "COMEN01C PROCESS-ENTER-KEY (INQUIRE PROGRAM)"
                    : "COMEN01C PROCESS-ENTER-KEY";
            throw new LegacyRuleException("This option " + opt.name() + " is not installed...", "option", para, 501);
        }
        return new Selection(opt.program(), opt.transaction());
    }

    /**
     * BMS OPTION field is NUM with JUSTIFY=(RIGHT,ZERO): "1" arrives as "01". Then
     * INSPECT REPLACING ALL ' ' BY '0' and the NUMERIC test.
     */
    static int parseOption(String text) {
        if (text == null) {
            return 0;
        }
        String t = text.strip();
        if (t.isEmpty() || t.length() > 2 || !t.chars().allMatch(Character::isDigit)) {
            return t.isEmpty() ? 0 : -1;
        }
        return Integer.parseInt(t);
    }
}
