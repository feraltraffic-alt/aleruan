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
@SpringBootTest(properties = {"dealos.security.enabled=false", "dealos.registry.live=false", "dealos.rates.live=false",
        "dealos.rates.offline.EUR=25.000", "dealos.import.worker-enabled=false",
        "dealos.import.anthropic-api-key="})
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
        /**
         * Stands in for Claude: test PDFs carry the extraction as JSON on a comment line ("% {...}"),
         * and a document containing FAIL fails to read.
         */
        @Bean
        @org.springframework.context.annotation.Primary
        com.aleruan.dealos.docimport.reader.DocumentReader stubReader(com.fasterxml.jackson.databind.ObjectMapper json) {
            return new com.aleruan.dealos.docimport.reader.DocumentReader() {
                public Result read(byte[] data, String contentType, String fileName) {
                    String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    if (text.contains("FAIL")) {
                        throw new ReadFailed("Claude is busy right now; the document will be read again later", true, null);
                    }
                    String line = text.lines().filter(l -> l.startsWith("% {")).findFirst()
                            .orElseThrow(() -> new ReadFailed("No data in the test document"));
                    try {
                        return new Result(json.readValue(line.substring(2),
                                com.aleruan.dealos.docimport.reader.Extraction.class), "stub", 100, 50);
                    } catch (java.io.IOException e) {
                        throw new ReadFailed("Bad test document", false, e);
                    }
                }

                public String model() {
                    return "stub";
                }
            };
        }

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
