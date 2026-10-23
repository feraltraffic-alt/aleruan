package com.aleruan.dealos.quote.application;

import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.deal.DealCatalog.ChangeOrderSummary;
import com.aleruan.dealos.platform.evidence.EvidenceCollector;
import com.aleruan.dealos.platform.evidence.EvidenceContributor;
import com.aleruan.dealos.platform.evidence.EvidenceEntry;
import com.aleruan.dealos.platform.evidence.EvidenceFile;
import com.aleruan.dealos.platform.evidence.EvidenceSection;
import com.aleruan.dealos.quote.domain.QuoteRepository;
import com.aleruan.dealos.quote.domain.QuoteSummary;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Quotes as issued (stored PDF and hash) and the sheets of approved change orders. */
@Component
class QuoteEvidence implements EvidenceContributor {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    private final QuoteRepository quotes;
    private final QuoteService service;
    private final DealCatalog deals;

    QuoteEvidence(QuoteRepository quotes, QuoteService service, DealCatalog deals) {
        this.quotes = quotes;
        this.service = service;
        this.deals = deals;
    }

    @Override
    public void contribute(UUID dealId, EvidenceCollector out) {
        List<QuoteSummary> issued = quotes.summaries(dealId).stream()
                .sorted(Comparator.comparingInt(QuoteSummary::version)).toList();
        for (QuoteSummary q : issued) {
            UUID id = q.id();
            out.entry(EvidenceEntry.of(EvidenceSection.QUOTES, q.displayNumber())
                    .date(q.issuedOn())
                    .by(q.issuedBy())
                    .amount(q.total(), q.currency())
                    .reference(q.displayNumber())
                    .file(EvidenceFile.pdf(q.displayNumber().replace('/', '-') + ".pdf", q.pdfSha256(),
                            () -> quotes.pdf(id)))
                    .build());
            out.audit(QuoteService.AUDIT_TYPE, id);
        }
        if (issued.isEmpty()) {
            out.missing("QUOTE_ISSUED", Map.of());
        } else {
            out.ok("QUOTE_ISSUED", Map.of("number", issued.get(issued.size() - 1).displayNumber()));
        }

        String currency = deals.find(dealId).map(DealCatalog.DealSummary::currency).orElse(null);
        for (ChangeOrderSummary co : deals.changeOrders(dealId)) {
            out.audit("ChangeOrder", co.id());
            if (!"APPROVED".equals(co.status())) {
                if ("SENT".equals(co.status())) {
                    out.warn("CHANGE_ORDER_UNDECIDED", Map.of("number", co.number()));
                }
                continue;
            }
            UUID id = co.id();
            out.entry(EvidenceEntry.of(EvidenceSection.CHANGE_ORDERS, co.number() + " " + co.title())
                    .date(co.decidedAt() == null ? null : co.decidedAt().atZone(PRAGUE).toLocalDate())
                    .status(co.status())
                    .amount(co.total(), currency)
                    .reference(co.number())
                    .by(co.decidedBy())
                    .file(EvidenceFile.pdf(co.number() + ".pdf", null, () -> service.changeOrderPdf(id, null)))
                    .build());
        }
    }
}
