package com.aleruan.dealos.platform.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Who is acting right now: the Keycloak username, or "system" for background jobs and local mode. */
@Component
public class CurrentActor {

    public static final String SYSTEM = "system";

    public String name() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        if (auth.getPrincipal() instanceof Jwt jwt && jwt.getClaimAsString("preferred_username") != null) {
            return jwt.getClaimAsString("preferred_username");
        }
        return auth.getName();
    }
}
