package com.carddemo.online.web;

import com.carddemo.online.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "User administration", description = "legacy: COUSR00C/CU00 .. COUSR03C/CU03 (admin only)")
public class UserAdminController {
    private final UserAdminService users;

    public UserAdminController(UserAdminService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "User list, 10 per page (COUSR00C)")
    public UserAdminService.UserPage list(@RequestParam(required = false) String fromId,
                                          @RequestParam(required = false) String after,
                                          @RequestParam(required = false) String before,
                                          @RequestParam(defaultValue = "1") int page) {
        return users.list(fromId, after, before, page);
    }

    @PostMapping
    @Operation(summary = "Add user (COUSR01C)")
    public UserAdminService.Result add(@RequestBody UserAdminService.UserRequest req) {
        return users.add(req);
    }

    @GetMapping("/detail")
    @Operation(summary = "Fetch user for update or delete (COUSR02C / COUSR03C)")
    public UserAdminService.UserDetail fetch(@RequestParam(required = false) String userId,
                                             @RequestParam(defaultValue = "false") boolean forDelete) {
        return users.fetch(userId, forDelete);
    }

    @PutMapping
    @Operation(summary = "Update user (COUSR02C PF5)")
    public UserAdminService.Result update(@RequestBody UserAdminService.UserRequest req) {
        return users.update(req);
    }

    @DeleteMapping
    @Operation(summary = "Delete user (COUSR03C PF5)")
    public UserAdminService.Result delete(@RequestParam(required = false) String userId) {
        return users.delete(userId);
    }
}
