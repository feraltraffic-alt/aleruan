package com.aleruan.dealos.platform.security;

import static com.aleruan.dealos.platform.security.Roles.ACCOUNTANT;
import static com.aleruan.dealos.platform.security.Roles.ACCOUNT_MANAGER;
import static com.aleruan.dealos.platform.security.Roles.ADMIN;
import static com.aleruan.dealos.platform.security.Roles.FOREMAN;
import static com.aleruan.dealos.platform.security.Roles.PROJECT_MANAGER;
import static com.aleruan.dealos.platform.security.Roles.SALES_MANAGER;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import jakarta.servlet.DispatcherType;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Who may do what, by URL, in one place (docs/ROLES.md explains it in plain words). Deny by default:
 * a new endpoint is closed until it is added here. The frontend mirrors this in src/permissions.ts.
 */
final class AccessRules {

    static final String[] ALL = Roles.ALL;
    /** Office staff: see deals with costs and margins, quotes, tenders, invoices. */
    static final String[] OFFICE = {ADMIN, SALES_MANAGER, ACCOUNT_MANAGER, ACCOUNTANT, PROJECT_MANAGER};
    /** Prices, quotes and deal status. */
    static final String[] SALES = {ADMIN, SALES_MANAGER, ACCOUNT_MANAGER};
    /** Invoices, payments, credit notes. */
    static final String[] FINANCE = {ADMIN, ACCOUNTANT, ACCOUNT_MANAGER};
    /** Running sites: schedule, acceptance, tenders and change orders for the work. */
    static final String[] PROJECT = {ADMIN, PROJECT_MANAGER};
    static final String[] SALES_OR_PROJECT = {ADMIN, SALES_MANAGER, ACCOUNT_MANAGER, PROJECT_MANAGER};
    /** People on site: diary and defects. */
    static final String[] SITE_CREW = {ADMIN, PROJECT_MANAGER, FOREMAN};
    /** Everyone who works with sites, without deal money. */
    static final String[] SITE_READ = {ADMIN, SALES_MANAGER, ACCOUNT_MANAGER, ACCOUNTANT, PROJECT_MANAGER, FOREMAN};
    static final String[] AUDIT = {ADMIN, ACCOUNTANT, ACCOUNT_MANAGER};

    private AccessRules() {
    }

    static void apply(AuthorizeHttpRequestsConfigurer<org.springframework.security.config.annotation.web.builders.HttpSecurity>
                              .AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                // error pages and forwards carry the original decision
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                // subcontractor offer pages: authorised by the unguessable token in the URL
                .requestMatchers("/api/public/**").permitAll()

                .requestMatchers(GET, "/api/config", "/api/reference/**").hasAnyRole(ALL)
                .requestMatchers("/api/reference/**").hasRole(ADMIN)
                .requestMatchers("/api/audit/**").hasAnyRole(AUDIT)
                .requestMatchers(POST, "/api/sanctions/import").hasRole(ADMIN)
                .requestMatchers(GET, "/api/sanctions/**").hasAnyRole(OFFICE)

                // counterparties: everyone picks subcontractors and suppliers; the office adds and checks them
                .requestMatchers(GET, "/api/counterparties/**").hasAnyRole(ALL)
                .requestMatchers("/api/counterparties/**").hasAnyRole(OFFICE)

                // deals and what hangs off them
                .requestMatchers(POST, "/api/deals/*/invoices").hasAnyRole(FINANCE)
                // evidence package: anyone in the office may build it; contract papers by sales or project
                .requestMatchers(POST, "/api/deals/*/evidence/export").hasAnyRole(OFFICE)
                .requestMatchers(POST, "/api/deals/*/documents").hasAnyRole(SALES_OR_PROJECT)
                .requestMatchers(DELETE, "/api/deals/*/documents/*").hasAnyRole(SALES_OR_PROJECT)
                .requestMatchers(POST, "/api/deals/*/site").hasAnyRole(SALES_OR_PROJECT)
                .requestMatchers(POST, "/api/deals/*/change-orders", "/api/deals/*/rfqs").hasAnyRole(SALES_OR_PROJECT)
                .requestMatchers(GET, "/api/deals/**", "/api/quotes/**", "/api/change-orders/**", "/api/rfqs/**")
                .hasAnyRole(OFFICE)
                .requestMatchers("/api/change-orders/**", "/api/rfqs/**").hasAnyRole(SALES_OR_PROJECT)
                .requestMatchers("/api/deals/**").hasAnyRole(SALES)

                // document import: the office uploads, reviews and confirms
                .requestMatchers("/api/imports/**").hasAnyRole(OFFICE)

                // analytics: margins and risks are office matters
                .requestMatchers(GET, "/api/analytics/**").hasAnyRole(OFFICE)

                // invoices, issued and received
                .requestMatchers(GET, "/api/invoices/**", "/api/supplier-invoices/**").hasAnyRole(OFFICE)
                .requestMatchers("/api/supplier-invoices/**").hasAnyRole(FINANCE)
                .requestMatchers("/api/invoices/**").hasAnyRole(FINANCE)

                // sites: diary and defects by the crew, everything else by project management
                .requestMatchers(POST, "/api/sites/*/diary", "/api/sites/*/defects").hasAnyRole(SITE_CREW)
                .requestMatchers(POST, "/api/diary/**", "/api/defects/**").hasAnyRole(SITE_CREW)
                .requestMatchers(PUT, "/api/diary/**", "/api/defects/**").hasAnyRole(SITE_CREW)
                .requestMatchers(DELETE, "/api/diary/**", "/api/defects/**").hasAnyRole(SITE_CREW)
                .requestMatchers(GET, "/api/sites/**", "/api/workload", "/api/defects", "/api/stages/**", "/api/files/**")
                .hasAnyRole(SITE_READ)
                .requestMatchers("/api/sites/**", "/api/stages/**").hasAnyRole(PROJECT)

                .anyRequest().denyAll();
    }
}
