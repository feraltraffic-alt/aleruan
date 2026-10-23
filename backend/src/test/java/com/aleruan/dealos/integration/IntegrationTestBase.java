package com.aleruan.dealos.integration;

import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.registry.LookupRequest;
import com.aleruan.dealos.counterparty.registry.RegistryClient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL container and one Spring context for all integration tests.
 * Live registries are off; a stub ARES answers for Czech companies so no test touches the network.
 */
@SpringBootTest(properties = {"dealos.security.enabled=false", "dealos.registry.live=false"})
@Import(IntegrationTestBase.StubRegistry.class)
abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class StubRegistry {
        @Bean
        RegistryClient stubAres() {
            return new RegistryClient() {
                public CheckSource source() { return CheckSource.ARES; }
                public boolean supports(LookupRequest r) { return "CZ".equals(r.country()); }
                public Optional<RegistrySnapshot> lookup(LookupRequest r) {
                    return Optional.of(RegistrySnapshot.builder(CheckSource.ARES, Instant.now()).found(true)
                            .legalName("Stub s.r.o.").registeredOn(LocalDate.parse("2020-01-01"))
                            .insolvency(false).rawResponse("{}").build());
                }
            };
        }
    }
}
