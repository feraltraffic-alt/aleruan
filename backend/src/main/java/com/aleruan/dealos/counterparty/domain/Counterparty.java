package com.aleruan.dealos.counterparty.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** One legal entity Aleruan deals with, in any role (a company can be customer and supplier at once). */
@Entity
@Table(name = "counterparty")
public class Counterparty {

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(name = "company_id")
    private String companyId;

    @Column(name = "vat_number")
    private String vatNumber;

    private String address;
    private String email;
    private String phone;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "counterparty_role", joinColumns = @JoinColumn(name = "counterparty_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Set<CounterpartyRole> roles = EnumSet.noneOf(CounterpartyRole.class);

    /** Work type codes (reference catalogue) a subcontractor or supplier covers. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "counterparty_work_type", joinColumns = @JoinColumn(name = "counterparty_id"))
    @Column(name = "work_type")
    private Set<String> workTypes = new java.util.HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "check_status")
    private CheckStatus checkStatus;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Counterparty() {
    }

    public Counterparty(String legalName, String country, String companyId, String vatNumber, String address,
                        String email, String phone, Set<CounterpartyRole> roles, Instant createdAt) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("A counterparty needs at least one role");
        }
        this.id = UUID.randomUUID();
        this.legalName = legalName;
        this.country = country;
        this.companyId = companyId;
        this.vatNumber = vatNumber;
        this.address = address;
        this.email = email;
        this.phone = phone;
        this.roles = EnumSet.copyOf(roles);
        this.createdAt = createdAt;
    }

    /** True when the role was new. */
    public boolean addRole(CounterpartyRole role) {
        return roles.add(role);
    }

    public void setWorkTypes(Set<String> codes) {
        this.workTypes = codes == null ? new java.util.HashSet<>() : new java.util.HashSet<>(codes);
    }

    public void applyVerdict(CheckStatus status, Instant checkedAt) {
        this.checkStatus = status;
        this.lastCheckedAt = checkedAt;
    }

    public UUID getId() { return id; }
    public String getLegalName() { return legalName; }
    public String getCountry() { return country; }
    public String getCompanyId() { return companyId; }
    public String getVatNumber() { return vatNumber; }
    public String getAddress() { return address; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Set<CounterpartyRole> getRoles() { return Set.copyOf(roles); }
    public Set<String> getWorkTypes() { return Set.copyOf(workTypes); }
    public CheckStatus getCheckStatus() { return checkStatus; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
