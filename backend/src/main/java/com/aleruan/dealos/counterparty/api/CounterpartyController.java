package com.aleruan.dealos.counterparty.api;

import com.aleruan.dealos.counterparty.application.AccountCheckService;
import com.aleruan.dealos.counterparty.application.CounterpartyCheckService;
import com.aleruan.dealos.counterparty.application.CounterpartyService;
import com.aleruan.dealos.counterparty.domain.CheckFinding;
import com.aleruan.dealos.counterparty.domain.CheckResult;
import com.aleruan.dealos.counterparty.domain.CheckSource;
import com.aleruan.dealos.counterparty.domain.CheckStatus;
import com.aleruan.dealos.counterparty.domain.Counterparty;
import com.aleruan.dealos.counterparty.domain.CounterpartyRole;
import com.aleruan.dealos.counterparty.domain.RegistrySnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/counterparties")
class CounterpartyController {

    record CreateRequest(
            @NotBlank String legalName,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String country,
            String companyId,
            String vatNumber,
            String address,
            @Email String email,
            String phone,
            @NotEmpty Set<CounterpartyRole> roles,
            Set<String> workTypes) {
    }

    record WorkTypesRequest(Set<String> workTypes) {
    }

    record CounterpartyView(UUID id, String legalName, String country, String companyId, String vatNumber,
                            String address, String email, String phone, Set<CounterpartyRole> roles,
                            Set<String> workTypes, CheckStatus checkStatus, Instant lastCheckedAt, Instant createdAt) {
        static CounterpartyView of(Counterparty c) {
            return new CounterpartyView(c.getId(), c.getLegalName(), c.getCountry(), c.getCompanyId(),
                    c.getVatNumber(), c.getAddress(), c.getEmail(), c.getPhone(), c.getRoles(), c.getWorkTypes(),
                    c.getCheckStatus(), c.getLastCheckedAt(), c.getCreatedAt());
        }
    }

    record CheckResultView(UUID id, CheckSource source, CheckStatus status, List<CheckFinding> findings,
                           String requestId, String unavailableReason, Instant checkedAt, String checkedAccount) {
        static CheckResultView of(CheckResult r) {
            return new CheckResultView(r.getId(), r.getSource(), r.getStatus(), r.getFindings(),
                    r.getRequestId(), r.getUnavailableReason(), r.getCheckedAt(), r.getCheckedAccount());
        }
    }

    record AccountCheckRequest(@NotBlank String account) {
    }

    record CheckRunView(CheckStatus status, List<CheckFinding> findings, List<CheckResultView> results) {
    }

    record LookupView(CheckSource source, String legalName, String address, String vatNumber,
                      LocalDate registeredOn) {
        static LookupView of(RegistrySnapshot s) {
            return new LookupView(s.source(), s.legalName(), s.address(), s.vatNumber(), s.registeredOn());
        }
    }

    private final CounterpartyService service;
    private final CounterpartyCheckService checks;
    private final AccountCheckService accountChecks;

    CounterpartyController(CounterpartyService service, CounterpartyCheckService checks,
                           AccountCheckService accountChecks) {
        this.service = service;
        this.checks = checks;
        this.accountChecks = accountChecks;
    }

    @GetMapping
    List<CounterpartyView> search(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) CounterpartyRole role) {
        return service.search(q, role).stream().map(CounterpartyView::of).toList();
    }

    @GetMapping("/{id}")
    CounterpartyView get(@PathVariable UUID id) {
        return CounterpartyView.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CounterpartyView create(@Valid @RequestBody CreateRequest r) {
        return CounterpartyView.of(service.create(new CounterpartyService.NewCounterparty(
                r.legalName(), r.country(), r.companyId(), r.vatNumber(), r.address(), r.email(), r.phone(),
                r.roles(), r.workTypes() == null ? Set.of() : r.workTypes())));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/work-types")
    CounterpartyView setWorkTypes(@PathVariable UUID id, @RequestBody WorkTypesRequest r) {
        return CounterpartyView.of(service.setWorkTypes(id, r.workTypes() == null ? Set.of() : r.workTypes()));
    }

    @PostMapping("/{id}/checks")
    CheckRunView runChecks(@PathVariable UUID id) {
        CounterpartyCheckService.CheckRun run = checks.check(id);
        return new CheckRunView(run.verdict().status(), run.verdict().findings(),
                run.results().stream().map(CheckResultView::of).toList());
    }

    /** Is this the account the tax registry publishes for this counterparty? */
    @PostMapping("/{id}/account-checks")
    CheckResultView checkAccount(@PathVariable UUID id, @Valid @RequestBody AccountCheckRequest r) {
        return CheckResultView.of(accountChecks.check(id, r.account()).result());
    }

    @GetMapping("/{id}/checks")
    List<CheckResultView> checkHistory(@PathVariable UUID id) {
        return checks.history(id).stream().map(CheckResultView::of).toList();
    }

    /** Pre-fill: GET /api/counterparties/lookup?country=CZ&companyId=14220997 */
    @GetMapping("/lookup")
    ResponseEntity<LookupView> lookup(@RequestParam String country,
                                      @RequestParam(required = false) String companyId,
                                      @RequestParam(required = false) String vatNumber) {
        return checks.lookup(country.toUpperCase(), companyId, vatNumber)
                .map(LookupView::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
