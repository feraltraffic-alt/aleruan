package com.aleruan.dealos.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aleruan.dealos.counterparty.application.CounterpartyCheckService;
import com.aleruan.dealos.counterparty.application.CounterpartyService;
import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.CheckStatus;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import com.aleruan.dealos.counterparty.application.AccountCheckService;
import com.aleruan.dealos.counterparty.domain.CheckFinding;
import com.aleruan.dealos.counterparty.registry.LookupRequest;
import com.aleruan.dealos.counterparty.registry.RegistryClient;
import com.aleruan.dealos.counterparty.sanctions.SanctionsService;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.web.ConflictException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end against real PostgreSQL: Flyway migrations, audit chain + append-only trigger,
 * counterparty creation, duplicate protection, checks with a stub registry (no network in CI).
 */
@SpringBootTest(properties = {"dealos.security.enabled=false", "dealos.registry.live=false"})
@Testcontainers
class AuditAndCounterpartyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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

    @Autowired CounterpartyService counterparties;
    @Autowired CounterpartyCheckService checks;
    @Autowired AuditService audit;
    @Autowired SanctionsService sanctions;
    @Autowired AccountCheckService accountChecks;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsChecksAndAuditsCounterparty() {
        Counterparty c = counterparties.create(new CounterpartyService.NewCounterparty(
                "Aleruan-industry s.r.o.", "cz", "142 20 997", "CZ14220997", null, null, null,
                Set.of(CounterpartyRole.CUSTOMER)));
        assertEquals("14220997", c.getCompanyId());

        CounterpartyCheckService.CheckRun run = checks.check(c.getId());
        assertTrue(run.results().stream().anyMatch(r -> r.getSource() == CheckSource.ARES));
        assertEquals(CheckStatus.GREEN, run.verdict().status());
        assertEquals(CheckStatus.GREEN, counterparties.get(c.getId()).getCheckStatus());

        assertEquals(2, audit.history("Counterparty", c.getId().toString()).size()); // CREATED + CHECKED
        assertTrue(audit.verifyChain().valid());

        assertThrows(ConflictException.class, () -> counterparties.create(new CounterpartyService.NewCounterparty(
                "Duplicate", "CZ", "14220997", null, null, null, null, Set.of(CounterpartyRole.SUPPLIER))));
    }

    @Test
    void auditTableIsAppendOnly() {
        audit.record("Test", "1", "CREATED", null);
        assertThrows(Exception.class, () -> jdbc.update("update audit_event set actor = 'mallory'"));
        assertThrows(Exception.class, () -> jdbc.update("delete from audit_event"));
        assertTrue(audit.verifyChain().valid());
    }

    @Test
    void sanctionsImportFlagsANameMatchForManualReview() throws Exception {
        String xml = """
                <export xmlns="http://eu.europa.ec/fpi/fsd/export">
                  <sanctionEntity logicalId="999"><subjectType code="enterprise"/>
                    <nameAlias wholeName="Fiktivni Sankce s.r.o."/></sanctionEntity>
                </export>""";
        assertTrue(sanctions.importFile(xml.getBytes(StandardCharsets.UTF_8)).isPresent());
        assertTrue(sanctions.importFile(xml.getBytes(StandardCharsets.UTF_8)).isEmpty()); // same file: skipped

        Counterparty c = counterparties.create(new CounterpartyService.NewCounterparty(
                "FIKTIVNI SANKCE, s.r.o.", "MD", "1003600000001", null, null, null, null,
                Set.of(CounterpartyRole.SUBCONTRACTOR)));
        CounterpartyCheckService.CheckRun run = checks.check(c.getId());
        assertTrue(run.verdict().findings().contains(CheckFinding.SANCTIONS_POSSIBLE_MATCH));
        assertTrue(run.verdict().findings().contains(CheckFinding.MANUAL_REVIEW_REQUIRED)); // no registry for MD
        assertEquals(CheckStatus.YELLOW, run.verdict().status());
    }

    @Test
    void accountCheckWithoutRegistryAsksForManualReview() {
        Counterparty c = counterparties.create(new CounterpartyService.NewCounterparty(
                "Softline Dev S.R.L.", "MD", "1009600012345", null, null, null, null,
                Set.of(CounterpartyRole.SUBCONTRACTOR)));
        AccountCheckService.AccountCheck check = accountChecks.check(c.getId(), "MD24 AG00 0225 1000 1310 4168");
        assertEquals(CheckStatus.YELLOW, check.verdict().status());
        assertEquals("MD24AG000225100013104168", check.result().getCheckedAccount());
    }
}
