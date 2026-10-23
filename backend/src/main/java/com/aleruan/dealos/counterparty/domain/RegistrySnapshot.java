package com.aleruan.dealos.counterparty.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Normalised answer of one registry. Null fields mean "this registry does not say".
 * {@code rawResponse} and {@code requestId} are kept as evidence.
 */
public record RegistrySnapshot(
        CheckSource source,
        Instant retrievedAt,
        boolean available,
        String unavailableReason,
        Boolean found,
        String requestId,
        String legalName,
        String address,
        String vatNumber,
        LocalDate registeredOn,
        LocalDate ceasedOn,
        Boolean vatValid,
        Boolean unreliableVatPayer,
        String vatStatus,
        Boolean insolvency,
        List<String> publishedAccounts,
        String rawResponse) {

    public RegistrySnapshot {
        publishedAccounts = publishedAccounts == null ? List.of() : List.copyOf(publishedAccounts);
    }

    public static RegistrySnapshot unavailable(CheckSource source, Instant at, String reason, String raw) {
        return builder(source, at).available(false).unavailableReason(reason).rawResponse(raw).build();
    }

    public static Builder builder(CheckSource source, Instant retrievedAt) {
        return new Builder(source, retrievedAt);
    }

    public static final class Builder {
        private final CheckSource source;
        private final Instant retrievedAt;
        private boolean available = true;
        private String unavailableReason;
        private Boolean found;
        private String requestId;
        private String legalName;
        private String address;
        private String vatNumber;
        private LocalDate registeredOn;
        private LocalDate ceasedOn;
        private Boolean vatValid;
        private Boolean unreliableVatPayer;
        private String vatStatus;
        private Boolean insolvency;
        private List<String> publishedAccounts;
        private String rawResponse;

        private Builder(CheckSource source, Instant retrievedAt) {
            this.source = source;
            this.retrievedAt = retrievedAt;
        }

        public Builder available(boolean v) { this.available = v; return this; }
        public Builder unavailableReason(String v) { this.unavailableReason = v; return this; }
        public Builder found(Boolean v) { this.found = v; return this; }
        public Builder requestId(String v) { this.requestId = v; return this; }
        public Builder legalName(String v) { this.legalName = v; return this; }
        public Builder address(String v) { this.address = v; return this; }
        public Builder vatNumber(String v) { this.vatNumber = v; return this; }
        public Builder registeredOn(LocalDate v) { this.registeredOn = v; return this; }
        public Builder ceasedOn(LocalDate v) { this.ceasedOn = v; return this; }
        public Builder vatValid(Boolean v) { this.vatValid = v; return this; }
        public Builder unreliableVatPayer(Boolean v) { this.unreliableVatPayer = v; return this; }
        public Builder vatStatus(String v) { this.vatStatus = v; return this; }
        public Builder insolvency(Boolean v) { this.insolvency = v; return this; }
        public Builder publishedAccounts(List<String> v) { this.publishedAccounts = v; return this; }
        public Builder rawResponse(String v) { this.rawResponse = v; return this; }

        public RegistrySnapshot build() {
            return new RegistrySnapshot(source, retrievedAt, available, unavailableReason, found, requestId,
                    legalName, address, vatNumber, registeredOn, ceasedOn, vatValid, unreliableVatPayer,
                    vatStatus, insolvency, publishedAccounts, rawResponse);
        }
    }
}
