package com.carddemo.online.web;

import com.carddemo.online.config.SessionUser;
import com.carddemo.online.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menu")
@Tag(name = "Menus", description = "legacy: COMEN01C/CM00, COADM01C/CA00")
public class MenuController {
    private final MenuService menus;

    public MenuController(MenuService menus) {
        this.menus = menus;
    }

    public record SelectRequest(String option) {
    }

    @GetMapping
    @Operation(summary = "Menu options for the signed-on user type (COMEN02Y / COADM02Y)")
    public List<MenuService.MenuOption> menu(@AuthenticationPrincipal SessionUser user) {
        return menus.menuFor(user);
    }

    @PostMapping("/select")
    @Operation(summary = "Validate an option number and return the target program (PROCESS-ENTER-KEY)")
    public MenuService.Selection select(@AuthenticationPrincipal SessionUser user, @RequestBody SelectRequest req) {
        return menus.select(user, req.option());
    }
}
