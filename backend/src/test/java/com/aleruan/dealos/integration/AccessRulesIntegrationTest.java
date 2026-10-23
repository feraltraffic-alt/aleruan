package com.aleruan.dealos.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The role matrix with real security on: who gets 403 and who gets through to the business rules. */
@SpringBootTest(properties = {"dealos.security.enabled=true", "dealos.registry.live=false", "dealos.rates.live=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:1/realms/test"})
@AutoConfigureMockMvc
class AccessRulesIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationTestBase.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", IntegrationTestBase.POSTGRES::getUsername);
        registry.add("spring.datasource.password", IntegrationTestBase.POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;

    private static final String ANY = UUID.randomUUID().toString();
    private static final ResultMatcher DENIED = status().isForbidden();
    /** Allowed through security: whatever the business answer is, it is not 401/403. */
    private static final ResultMatcher ALLOWED = r -> {
        int s = r.getResponse().getStatus();
        if (s == 401 || s == 403) {
            throw new AssertionError("expected access, got " + s + " for " + r.getRequest().getRequestURI());
        }
    };

    private void as(String role, MockHttpServletRequestBuilder req, ResultMatcher expected) throws Exception {
        var auth = role == null ? new SimpleGrantedAuthority[0] : new SimpleGrantedAuthority[] {new SimpleGrantedAuthority("ROLE_" + role)};
        mvc.perform(req.with(jwt().authorities(Arrays.asList(auth))).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(expected);
    }

    @Test
    void anonymousCallersNeedALoginExceptOnPublicLinks() throws Exception {
        mvc.perform(get("/api/deals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/public/rfq/" + "A".repeat(43))).andExpect(status().isNotFound()); // well-formed, unknown
        mvc.perform(get("/api/public/rfq/not-a-token")).andExpect(status().isBadRequest());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void aLoginWithoutABusinessRoleGetsNothing() throws Exception {
        as(null, get("/api/config"), DENIED);
        as(null, get("/api/counterparties"), DENIED);
    }

    @Test
    void foremanWorksOnSitesButSeesNoMoney() throws Exception {
        as("FOREMAN", get("/api/sites"), ALLOWED);
        as("FOREMAN", post("/api/sites/" + ANY + "/diary"), ALLOWED);
        as("FOREMAN", post("/api/defects/" + ANY + "/status"), ALLOWED);
        as("FOREMAN", get("/api/counterparties"), ALLOWED);
        as("FOREMAN", get("/api/deals"), DENIED);
        as("FOREMAN", get("/api/invoices"), DENIED);
        as("FOREMAN", post("/api/deals/" + ANY + "/invoices"), DENIED);
        as("FOREMAN", post("/api/rfqs/" + ANY + "/award"), DENIED);
        as("FOREMAN", put("/api/sites/" + ANY), DENIED);
        as("FOREMAN", post("/api/stages/" + ANY + "/decision"), DENIED);
        as("FOREMAN", get("/api/audit/verify"), DENIED);
    }

    @Test
    void moneyMovesOnlyWithTheRightRole() throws Exception {
        as("ACCOUNTANT", post("/api/deals/" + ANY + "/invoices"), ALLOWED);
        as("ACCOUNTANT", post("/api/invoices/" + ANY + "/credit"), ALLOWED);
        as("ACCOUNTANT", put("/api/deals/" + ANY), DENIED);
        as("SALES_MANAGER", post("/api/invoices/" + ANY + "/issue"), DENIED);
        as("SALES_MANAGER", put("/api/deals/" + ANY), ALLOWED);
        as("SALES_MANAGER", post("/api/deals/" + ANY + "/quotes"), ALLOWED);
        as("PROJECT_MANAGER", post("/api/deals/" + ANY + "/quotes"), DENIED);
        as("PROJECT_MANAGER", post("/api/rfqs/" + ANY + "/award"), ALLOWED);
        as("PROJECT_MANAGER", post("/api/stages/" + ANY + "/decision"), ALLOWED);
        as("WAREHOUSE_KEEPER", get("/api/sites"), DENIED);
        as("WAREHOUSE_KEEPER", get("/api/files/" + ANY), DENIED);
        as("ADMIN", post("/api/reference/work-types"), ALLOWED);
        as("SALES_MANAGER", post("/api/reference/work-types"), DENIED);
        as("ADMIN", get("/api/no-such-endpoint"), DENIED);
    }

    @Test
    void documentImportAndReceivedInvoicesForTheOffice() throws Exception {
        as("ACCOUNTANT", get("/api/imports"), ALLOWED);
        as("PROJECT_MANAGER", post("/api/imports/" + ANY + "/reject"), ALLOWED);
        as("FOREMAN", get("/api/imports"), DENIED);
        as("FOREMAN", get("/api/imports/" + ANY + "/file"), DENIED);
        as("SALES_MANAGER", get("/api/supplier-invoices"), ALLOWED);
        as("SALES_MANAGER", post("/api/supplier-invoices/" + ANY + "/paid"), DENIED);
        as("ACCOUNTANT", post("/api/supplier-invoices/" + ANY + "/paid"), ALLOWED);
        as("FOREMAN", get("/api/supplier-invoices"), DENIED);
    }

    @Test
    void analyticsForTheOfficeOnly() throws Exception {
        as("ACCOUNTANT", get("/api/analytics/margin"), ALLOWED);
        as("PROJECT_MANAGER", get("/api/analytics/health"), ALLOWED);
        as("FOREMAN", get("/api/analytics/margin"), DENIED);
        as("WAREHOUSE_KEEPER", get("/api/analytics/health"), DENIED);
    }

    @Test
    void evidencePackageForTheOfficeContractPapersBySalesOrProject() throws Exception {
        as("ACCOUNTANT", get("/api/deals/" + ANY + "/evidence"), ALLOWED);
        as("ACCOUNTANT", post("/api/deals/" + ANY + "/evidence/export"), ALLOWED);
        as("PROJECT_MANAGER", post("/api/deals/" + ANY + "/evidence/export"), ALLOWED);
        as("FOREMAN", get("/api/deals/" + ANY + "/evidence"), DENIED);
        as("FOREMAN", post("/api/deals/" + ANY + "/evidence/export"), DENIED);
        as("PROJECT_MANAGER", post("/api/deals/" + ANY + "/documents"), ALLOWED);
        as("SALES_MANAGER", delete("/api/deals/" + ANY + "/documents/" + ANY), ALLOWED);
        as("ACCOUNTANT", post("/api/deals/" + ANY + "/documents"), DENIED);
        as("ACCOUNTANT", get("/api/deals/" + ANY + "/documents/" + ANY), ALLOWED);
        as("FOREMAN", get("/api/deals/" + ANY + "/documents/" + ANY), DENIED);
    }
}
