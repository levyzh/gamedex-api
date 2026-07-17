package com.gamedex.gamedex_api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    // @AuthenticationPrincipal hands us the validated JWT of whoever is
    // calling. By the time this method runs, Spring Security has already
    // verified the signature — a fake or expired token never gets here.
    // The "sub" (subject) claim is the Supabase user id — the exact value
    // auth.uid() returns in our RLS policies. This one line is the bridge
    // between Supabase identity and our Java code.
    @GetMapping("/api/me")
    public String me(@AuthenticationPrincipal Jwt jwt) {
        return "You are user: " + jwt.getSubject();
    }
}