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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.invoice.domain.SupplierInvoice;
import com.aleruan.dealos.invoice.domain.SupplierInvoiceRepository;
import com.aleruan.dealos.platform.files.FileStore;
import org.springframework.stereotype.Component;

/**
 * Issued invoices and credit notes with their stored PDFs. Flags an invoice that no accepted stage
 * stands behind (nothing in the package proves the work was handed over) and overdue ones.
 */
@Component
class InvoiceEvidence implements EvidenceContributor {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    private final InvoiceRepository invoices;
    private final SupplierInvoiceRepository supplierInvoices;
    private final FileStore files;
    private final DealCatalog deals;
    private final CounterpartyLookup counterparties;
    private final Clock clock;

    InvoiceEvidence(InvoiceRepository invoices, SupplierInvoiceRepository supplierInvoices, FileStore files,
                    DealCatalog deals, CounterpartyLookup counterparties, Clock clock) {
        this.invoices = invoices;
        this.supplierInvoices = supplierInvoices;
        this.files = files;
        this.deals = deals;
        this.counterparties = counterparties;
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
                    .file(EvidenceFile.storedPdf(i.getNumber() + ".pdf", i.getPdfSha256(), () -> invoices.pdf(id)))
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
        supplierSide(dealId, out);
    }

    /**
     * Invoices received from the deal's subcontractors and suppliers. Once the deal is completed, each one
     * that had costs on it should have invoiced.
     */
    private void supplierSide(UUID dealId, EvidenceCollector out) {
        List<SupplierInvoice> received = supplierInvoices.search(dealId, null).stream()
                .sorted(Comparator.comparing(SupplierInvoice::getIssueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SupplierInvoice::getNumber))
                .toList();
        Map<UUID, String> names = new HashMap<>();
        java.util.function.Function<UUID, String> name = id -> names.computeIfAbsent(id,
                k -> counterparties.find(k).map(CounterpartySummary::legalName).orElse(k.toString()));
        Map<UUID, com.aleruan.dealos.platform.files.FileInfo> fileInfo = files.list(SupplierInvoiceService.FILE_OWNER,
                received.stream().map(SupplierInvoice::getId).toList()).stream()
                .collect(Collectors.toMap(com.aleruan.dealos.platform.files.FileInfo::id, f -> f, (a, b2) -> a));
        for (SupplierInvoice s : received) {
            out.audit(SupplierInvoiceService.AUDIT_TYPE, s.getId());
            EvidenceEntry.Builder b = EvidenceEntry.of(EvidenceSection.INVOICES, s.getNumber() + " — " + name.apply(s.getSupplierId()))
                    .date(s.getIssueDate())
                    .by(s.getCreatedBy())
                    .status(s.getPaidOn() != null ? "PAID" : "RECEIVED")
                    .amount(s.getTotal(), s.getCurrency())
                    .reference(s.getVariableSymbol());
            com.aleruan.dealos.platform.files.FileInfo f = s.getFileId() == null ? null : fileInfo.get(s.getFileId());
            if (f != null) {
                UUID fileId = f.id();
                out.audit("File", fileId);
                b.file(EvidenceFile.stored("supplier-" + s.getNumber() + extension(f.contentType()), f.contentType(),
                        s.getFileSha256(), () -> files.data(fileId)));
            }
            out.entry(b.build());
        }

        boolean completed = deals.find(dealId).map(d -> "COMPLETED".equals(d.status())).orElse(false);
        if (!completed) {
            return;
        }
        Set<UUID> invoiced = received.stream().map(SupplierInvoice::getSupplierId).collect(Collectors.toSet());
        Set<UUID> expected = new LinkedHashSet<>();
        deals.lines(dealId).stream()
                .filter(l -> l.subcontractorId() != null && l.unitCost() != null && l.unitCost().signum() > 0)
                .forEach(l -> expected.add(l.subcontractorId()));
        for (UUID id : expected) {
            if (!invoiced.contains(id)) {
                out.warn("SUPPLIER_INVOICE_MISSING", Map.of("name", name.apply(id)));
            }
        }
        if (!expected.isEmpty() && invoiced.containsAll(expected)) {
            out.ok("SUPPLIER_INVOICES", Map.of("count", Integer.toString(received.size())));
        }
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".pdf";
        };
    }

}
