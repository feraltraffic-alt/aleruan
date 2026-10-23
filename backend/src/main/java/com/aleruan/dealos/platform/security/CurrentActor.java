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

    /**
     * True when the signed-in user has one of the roles. Without a signed-in user (security off in local
     * mode, background jobs) everything is allowed, as the URL rules are off then too.
     */
    public boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true;
        }
        java.util.Set<String> granted = auth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        return java.util.Arrays.stream(roles).anyMatch(r -> granted.contains("ROLE_" + r));
    }

    /** For rules that depend on what a request does, not only on its URL; answered with 403. */
    public void require(String what, String... roles) {
        if (!hasAnyRole(roles)) {
            throw new org.springframework.security.access.AccessDeniedException("Your role may not " + what);
        }
    }

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
