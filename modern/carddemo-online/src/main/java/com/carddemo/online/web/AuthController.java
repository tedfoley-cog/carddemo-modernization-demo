package com.carddemo.online.web;

import com.carddemo.online.service.SignonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Sign-on", description = "legacy: COSGN00C/CC00")
public class AuthController {
    private final SignonService signon;

    public AuthController(SignonService signon) {
        this.signon = signon;
    }

    public record SignonRequest(String userId, String password) {
    }

    @PostMapping("/signon")
    @Operation(summary = "Sign on (COSGN00C PROCESS-ENTER-KEY); returns a bearer session token")
    public SignonService.SignonResult signon(@RequestBody SignonRequest req) {
        return signon.signon(req.userId(), req.password());
    }

    @PostMapping("/signoff")
    @Operation(summary = "Sign off (PF3 on COSGN00C / RETURN-TO-SIGNON-SCREEN)")
    public Map<String, String> signoff() {
        return Map.of("message", SignonService.MSG_SIGNOFF);
    }
}
