package com.aleruan.dealos.platform.security;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Keycloak (OIDC) resource server; realm roles become ROLE_* authorities.
 * With {@code dealos.security.enabled=false} (profile "local") every request runs as an anonymous
 * administrator so the whole app can be used without a Keycloak instance during development.
 */
@Configuration
@EnableMethodSecurity
@EnableScheduling
class SecurityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(name = "dealos.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain securedChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // stateless bearer-token API, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtConverter())))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "dealos.security.enabled", havingValue = "false")
    SecurityFilterChain openChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .anonymous(a -> a.principal(CurrentActor.SYSTEM).authorities(Roles.ALL_AUTHORITIES))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
