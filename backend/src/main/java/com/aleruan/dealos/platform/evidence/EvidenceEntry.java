package com.aleruan.dealos.platform.evidence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One document or record of the deal: a quote version, an invoice, a diary entry, a registry check.
 *
 * @param title     what it is, e.g. "FV-2026-0007" or "3. Cracked tile"
 * @param by        who issued, decided or wrote it, may be null
 * @param status    a status code the summary translates (APPROVED, ACCEPTED_WITH_REMARKS, PAID, OPEN, GREEN…), may be null
 * @param amount    money amount with {@code currency}, may be null
 * @param reference the identifier an outsider can check it by (document number, registry request id), may be null
 * @param text      longer text that belongs in the summary verbatim (a diary entry, remarks), may be null
 */
public record EvidenceEntry(EvidenceSection section, LocalDate date, String by, String title, String status, BigDecimal amount,
                            String currency, String reference, String text, List<EvidenceFile> files) {

    public EvidenceEntry {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(title, "title");
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static Builder of(EvidenceSection section, String title) {
        return new Builder(section, title);
    }

    /** Most entries fill only some fields. */
    public static final class Builder {
        private final EvidenceSection section;
        private final String title;
        private LocalDate date;
        private String by;
        private String status;
        private BigDecimal amount;
        private String currency;
        private String reference;
        private String text;
        private List<EvidenceFile> files = List.of();

        private Builder(EvidenceSection section, String title) {
            this.section = section;
            this.title = title;
        }

        public Builder date(LocalDate date) { this.date = date; return this; }
        public Builder by(String by) { this.by = by; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder amount(BigDecimal amount, String currency) { this.amount = amount; this.currency = currency; return this; }
        public Builder reference(String reference) { this.reference = reference; return this; }
        public Builder text(String text) { this.text = text; return this; }
        public Builder files(List<EvidenceFile> files) { this.files = files; return this; }
        public Builder file(EvidenceFile file) { this.files = List.of(file); return this; }

        public EvidenceEntry build() {
            return new EvidenceEntry(section, date, by, title, status, amount, currency, reference, text, files);
        }
    }
}
