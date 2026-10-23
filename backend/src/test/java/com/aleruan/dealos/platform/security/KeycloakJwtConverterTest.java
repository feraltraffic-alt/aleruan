package com.aleruan.dealos.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtConverterTest {

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
    }

    @Test
    void mapsKnownRealmRolesAndUsername() {
        Jwt token = jwt(Map.of("sub", "123", "preferred_username", "lena",
                "realm_access", Map.of("roles", List.of("ADMIN", "ACCOUNTANT", "offline_access", "default-roles-aleruan"))));
        var auth = new KeycloakJwtConverter().convert(token);
        assertEquals("lena", auth.getName());
        assertEquals(List.of("ROLE_ADMIN", "ROLE_ACCOUNTANT"),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    void noRealmAccessMeansNoRoles() {
        assertEquals(0, KeycloakJwtConverter.authorities(jwt(Map.of("sub", "x"))).size());
    }
}
