package com.aleruan.dealos.invoice.application;

import com.aleruan.dealos.invoice.domain.Invoice;
import com.aleruan.dealos.invoice.domain.InvoiceKind;
import com.aleruan.dealos.invoice.domain.InvoiceLine;
import com.aleruan.dealos.invoice.domain.InvoiceRepository;
import com.aleruan.dealos.invoice.domain.InvoiceStatus;
import com.aleruan.dealos.platform.evidence.EvidenceCollector;
import com.aleruan.dealos.platform.evidence.EvidenceContributor;
import com.aleruan.dealos.platform.evidence.EvidenceEntry;
import com.aleruan.dealos.platform.evidence.EvidenceFile;
import com.aleruan.dealos.platform.evidence.EvidenceSection;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Issued invoices and credit notes with their stored PDFs. Flags an invoice that no accepted stage
 * stands behind (nothing in the package proves the work was handed over) and overdue ones.
 */
@Component
class InvoiceEvidence implements EvidenceContributor {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    private final InvoiceRepository invoices;
    private final Clock clock;

    InvoiceEvidence(InvoiceRepository invoices, Clock clock) {
        this.invoices = invoices;
        this.clock = clock;
    }

    @Override
    public void contribute(UUID dealId, EvidenceCollector out) {
        LocalDate today = LocalDate.now(clock.withZone(PRAGUE));
        List<Invoice> issued = invoices.search(null, dealId, null).stream()
                .filter(i -> i.getStatus().isIssued())
                .sorted(Comparator.comparing(Invoice::getIssueDate).thenComparing(Invoice::getNumber))
                .toList();
        int backed = 0;
        int invoicesToCheck = 0;
        for (Invoice i : issued) {
            UUID id = i.getId();
            out.entry(EvidenceEntry.of(EvidenceSection.INVOICES, i.getNumber())
                    .date(i.getIssueDate())
                    .by(i.getIssuedBy())
                    .status(i.getStatus().name())
                    .amount(i.getTotal(), i.getCurrency())
                    .reference(i.getVariableSymbol())
                    .file(EvidenceFile.pdf(i.getNumber() + ".pdf", i.getPdfSha256(), () -> invoices.pdf(id)))
                    .build());
            out.audit(InvoiceService.AUDIT_TYPE, id);

            if (i.getKind() != InvoiceKind.INVOICE || i.getStatus() == InvoiceStatus.CREDITED) {
                continue;
            }
            invoicesToCheck++;
            if (i.getLines().stream().map(InvoiceLine::getStageId).anyMatch(Objects::nonNull)) {
                backed++;
            } else {
                out.warn("INVOICE_WITHOUT_ACCEPTANCE", Map.of("number", i.getNumber()));
            }
            if (i.isOverdue(today)) {
                out.warn("INVOICE_OVERDUE", Map.of("number", i.getNumber(), "due", i.getDueDate().toString()));
            }
        }
        if (invoicesToCheck > 0 && backed == invoicesToCheck) {
            out.ok("INVOICES_BACKED", Map.of("count", Integer.toString(backed)));
        }
    }
}
