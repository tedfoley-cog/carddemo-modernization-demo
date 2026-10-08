package com.carddemo.online.web;

import com.carddemo.online.service.AccountUpdateService;
import com.carddemo.online.service.AccountViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "legacy: COACTVWC/CAVW, COACTUPC/CAUP")
public class AccountController {
    private final AccountViewService view;
    private final AccountUpdateService update;

    public AccountController(AccountViewService view, AccountUpdateService update) {
        this.view = view;
        this.update = update;
    }

    @GetMapping
    @Operation(summary = "Account view (COACTVWC 9000-READ-ACCT)")
    public AccountViewService.AccountView view(@RequestParam(required = false) String accountId) {
        return view.view(accountId);
    }

    @GetMapping("/update")
    @Operation(summary = "Fetch account + customer for update (COACTUPC 9000-READ-ACCT); returns the snapshot to send back")
    public AccountUpdateService.AccountUpdateData fetchForUpdate(@RequestParam(required = false) String accountId) {
        return update.fetch(accountId);
    }

    @PostMapping("/update/validate")
    @Operation(summary = "Validate changes (COACTUPC 1200-EDIT-MAP-INPUTS); success = 'Changes validated.Press F5 to save'")
    public AccountUpdateService.Outcome validate(@RequestParam(required = false) String accountId,
                                                 @RequestBody AccountUpdateService.UpdateRequest req) {
        return update.validate(accountId, req);
    }

    @PutMapping("/update")
    @Operation(summary = "Commit validated changes (PF5, COACTUPC 9600-WRITE-PROCESSING)")
    public AccountUpdateService.Outcome save(@RequestParam(required = false) String accountId,
                                             @RequestBody AccountUpdateService.UpdateRequest req) {
        return update.save(accountId, req);
    }
}
