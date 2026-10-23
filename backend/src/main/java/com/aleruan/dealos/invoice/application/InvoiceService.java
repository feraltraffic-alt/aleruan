package com.aleruan.dealos.invoice.application;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.deal.DealCatalog.DealSummary;
import com.aleruan.dealos.invoice.domain.Invoice;
import com.aleruan.dealos.invoice.domain.InvoiceDocument;
import com.aleruan.dealos.invoice.domain.InvoiceHtml;
import com.aleruan.dealos.invoice.domain.InvoiceKind;
import com.aleruan.dealos.invoice.domain.InvoiceRepository;
import com.aleruan.dealos.invoice.domain.InvoiceSeries;
import com.aleruan.dealos.invoice.domain.InvoiceSeriesRepository;
import com.aleruan.dealos.invoice.domain.InvoiceStatus;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.documents.CompanyProfile;
import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.documents.PaymentQr;
import com.aleruan.dealos.platform.documents.PdfRenderer;
import com.aleruan.dealos.platform.documents.QrImage;
import com.aleruan.dealos.platform.documents.VatAdvisor;
import com.aleruan.dealos.platform.documents.VatMode;
import com.aleruan.dealos.platform.documents.VatNotes;
import com.aleruan.dealos.platform.rates.ExchangeRates;
import com.aleruan.dealos.platform.rates.RatesUnavailableException;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.NotFoundException;
import com.aleruan.dealos.site.SiteCatalog;
import com.aleruan.dealos.site.SiteCatalog.AcceptedStage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    public static final String AUDIT_TYPE = "Invoice";
    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final Set<String> INVOICEABLE = Set.of("WON", "IN_PROGRESS", "COMPLETED");

    public enum Source { STAGE, DEAL_LINES, EMPTY }

    public record Filter(InvoiceStatus status, UUID dealId, UUID customerId, boolean overdueOnly) {
    }

    /** Invoicing position of a deal: what is agreed, invoiced, paid, and which accepted stages are still open. */
    public record DealInvoicing(BigDecimal contractValue, BigDecimal invoiced, BigDecimal paid, BigDecimal outstanding,
                                BigDecimal remaining, String currency, List<StageInvoicing> stages) {
    }

    public record StageInvoicing(AcceptedStage stage, boolean invoiced) {
    }

    private final InvoiceRepository invoices;
    private final InvoiceSeriesRepository series;
    private final DealCatalog deals;
    private final CounterpartyLookup counterparties;
    private final SiteCatalog sites;
    private final ExchangeRates rates;
    private final PdfRenderer pdf;
    private final CompanyProfile company;
    private final AuditService audit;
    private final CurrentActor actor;
    private final ObjectMapper json;
    private final Clock clock;
    private final int dueDays;

    InvoiceService(InvoiceRepository invoices, InvoiceSeriesRepository series, DealCatalog deals,
                   CounterpartyLookup counterparties, SiteCatalog sites, ExchangeRates rates, PdfRenderer pdf,
                   CompanyProfile company, AuditService audit, CurrentActor actor, ObjectMapper json, Clock clock,
                   @Value("${dealos.invoices.due-days:14}") int dueDays) {
        this.invoices = invoices;
        this.series = series;
        this.deals = deals;
        this.counterparties = counterparties;
        this.sites = sites;
        this.rates = rates;
        this.pdf = pdf;
        this.company = company;
        this.audit = audit;
        this.actor = actor;
        this.json = json;
        this.clock = clock;
        this.dueDays = dueDays;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(PRAGUE));
    }

    // ---- drafts ----

    @Transactional
    public Invoice createDraft(UUID dealId, Source source, UUID stageId) {
        DealSummary deal = deal(dealId);
        if (!INVOICEABLE.contains(deal.status())) {
            throw new IllegalStateException("Deal " + deal.number() + " can be invoiced once the customer has accepted it");
        }
        invoices.lockDeal(dealId);
        CounterpartySummary customer = customer(deal);
        VatAdvisor.Suggestion vat = suggestion(deal, customer);
        LocalDate taxable = today();
        List<Invoice.LineData> lines = new ArrayList<>();
        switch (source) {
            case STAGE -> {
                AcceptedStage stage = stageOf(dealId, stageId);
                if (invoices.invoicedStages(dealId).contains(stage.id())) {
                    throw new IllegalStateException("Stage " + stage.seq() + " is already on an invoice");
                }
                lines.add(new Invoice.LineData(stageLine(stage), null, BigDecimal.ONE, stage.amount(), stage.id()));
                if (stage.acceptedAt() != null) {
                    taxable = LocalDate.ofInstant(stage.acceptedAt(), PRAGUE); // the supply happened at acceptance
                }
            }
            case DEAL_LINES -> {
                boolean anyInvoice = invoices.search(null, dealId, null).stream().anyMatch(i ->
                        i.getKind() == InvoiceKind.INVOICE && i.getStatus() != InvoiceStatus.CREDITED);
                if (anyInvoice) {
                    // the whole deal on one invoice is only safe while nothing has been invoiced yet
                    throw new IllegalStateException("Deal " + deal.number() + " already has invoices; invoice the "
                            + "remaining work by stages or with a blank invoice so nothing is billed twice");
                }
                deals.customerLines(dealId).forEach(l -> lines.add(new Invoice.LineData(l.description(),
                        l.unit(), l.quantity(), l.unitPrice(), null)));
            }
            case EMPTY -> { }
        }
        Invoice invoice = invoices.save(Invoice.draft(dealId, deal.customerId(), deal.currency(),
                new Invoice.Settings(DocLanguage.forCountry(customer.country()), vat.mode(), vat.rate(), taxable,
                        today().plusDays(dueDays), null),
                lines, clock.instant(), actor.name()));
        audit.record(AUDIT_TYPE, invoice.getId().toString(), "DRAFTED", Map.of("dealId", dealId, "source", source,
                "total", invoice.getTotal()));
        return invoice;
    }

    @Transactional
    public Invoice edit(UUID id, Invoice.Settings settings, List<Invoice.LineData> lines) {
        Invoice invoice = get(id);
        invoices.lockDeal(invoice.getDealId());
        Set<UUID> allowed = new HashSet<>(sites.acceptedStages(invoice.getDealId()).stream().map(AcceptedStage::id).toList());
        List<UUID> taken = invoices.invoicedStages(invoice.getDealId());
        Set<UUID> mine = new HashSet<>(invoice.getLines().stream().map(l -> l.getStageId()).filter(java.util.Objects::nonNull).toList());
        for (Invoice.LineData l : lines) {
            if (l.stageId() != null && (!allowed.contains(l.stageId()) || (taken.contains(l.stageId()) && !mine.contains(l.stageId())))) {
                throw new IllegalArgumentException("A stage line refers to a stage that is not accepted or already invoiced");
            }
        }
        invoice.edit(settings, lines);
        audit.record(AUDIT_TYPE, id.toString(), "EDITED", Map.of("total", invoice.getTotal(), "lines", lines.size()));
        return invoice;
    }

    @Transactional
    public void deleteDraft(UUID id) {
        Invoice invoice = get(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Issued invoices are never deleted; issue a credit note");
        }
        invoices.delete(invoice);
        audit.record(AUDIT_TYPE, id.toString(), "DRAFT_DELETED", Map.of("dealId", invoice.getDealId()));
    }

    // ---- issuing ----

    /**
     * Assigns the next number of the year, converts VAT to CZK at the ČNB rate for the date of
     * taxable supply (or the rate given by hand when the ČNB is unreachable) and stores the PDF.
     */
    @Transactional
    public Invoice issue(UUID id, BigDecimal manualRate) {
        Invoice invoice = get(id);
        invoices.lockDeal(invoice.getDealId());
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Invoice " + invoice.getNumber() + " is already issued");
        }
        List<UUID> elsewhere = invoices.search(null, invoice.getDealId(), null).stream()
                .filter(i -> !i.getId().equals(id) && i.getKind() == InvoiceKind.INVOICE
                        && i.getStatus() != InvoiceStatus.CREDITED && i.getStatus() != InvoiceStatus.DRAFT)
                .flatMap(i -> i.getLines().stream().map(l -> l.getStageId())).filter(java.util.Objects::nonNull).toList();
        if (invoice.getLines().stream().anyMatch(l -> l.getStageId() != null && elsewhere.contains(l.getStageId()))) {
            throw new IllegalStateException("A stage on this draft has been invoiced meanwhile; remove that line");
        }
        ExchangeRates.Rate rate = rateFor(invoice.getCurrency(), invoice.getTaxableDate(), manualRate);
        freeze(invoice, rate, null);
        return invoice;
    }

    @Transactional
    public Invoice pay(UUID id, BigDecimal amount, LocalDate on) {
        Invoice invoice = get(id);
        if (on != null && on.isAfter(today())) {
            throw new IllegalArgumentException("A payment is recorded once it has arrived");
        }
        invoice.pay(amount, on);
        audit.record(AUDIT_TYPE, id.toString(), "PAYMENT", Map.of("amount", amount, "on", on, "status", invoice.getStatus()));
        return invoice;
    }

    /** Reverses an issued invoice with a credit note, issued at once with the original's exchange rate. */
    @Transactional
    public Invoice credit(UUID id, String reason) {
        Invoice original = get(id);
        // written as a draft first: the database refuses lines on an already issued document
        Invoice note = invoices.saveAndFlush(Invoice.creditNoteFor(original, reason, today(), clock.instant(), actor.name()));
        ExchangeRates.Rate rate = original.getExchangeRate() == null ? null
                : new ExchangeRates.Rate(original.getCurrency(), original.getExchangeRate(), original.getRateDate(), "original");
        freeze(note, rate, original.getNumber());
        original.markCredited();
        audit.record(AUDIT_TYPE, id.toString(), "CREDITED", Map.of("creditNote", note.getNumber(), "reason", reason));
        return note;
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public Invoice get(UUID id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("Invoice " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Invoice> search(Filter f) {
        LocalDate today = today();
        return invoices.search(f.status(), f.dealId(), f.customerId()).stream()
                .filter(i -> !f.overdueOnly() || i.isOverdue(today)).toList();
    }

    @Transactional(readOnly = true)
    public String html(UUID id) {
        Invoice i = get(id);
        if (i.getDocument() == null) {
            throw new IllegalStateException("A draft has no document yet");
        }
        try {
            InvoiceDocument d = json.readValue(i.getDocument(), InvoiceDocument.class);
            return InvoiceHtml.render(d, pdf.logo(), d.paymentQr() == null ? null : QrImage.dataUri(d.paymentQr()), PdfRenderer.FONT);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored invoice " + id + " is unreadable", e);
        }
    }

    @Transactional(readOnly = true)
    public DealInvoicing dealInvoicing(UUID dealId) {
        DealSummary deal = deal(dealId);
        BigDecimal invoiced = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        for (Invoice i : invoices.search(null, dealId, null)) {
            if (i.getStatus() == InvoiceStatus.DRAFT) {
                continue;
            }
            invoiced = invoiced.add(i.getSubtotal()); // credited invoices cancel out with their credit notes
            paid = paid.add(i.getKind() == InvoiceKind.INVOICE ? i.getPaidAmount() : BigDecimal.ZERO);
            outstanding = outstanding.add(i.outstanding());
        }
        BigDecimal contract = deals.contractValue(dealId);
        List<UUID> taken = invoices.invoicedStages(dealId);
        List<StageInvoicing> stages = sites.acceptedStages(dealId).stream()
                .map(s -> new StageInvoicing(s, taken.contains(s.id()))).toList();
        return new DealInvoicing(contract, invoiced, paid, outstanding, contract.subtract(invoiced), deal.currency(), stages);
    }

    // ---- internals ----

    private ExchangeRates.Rate rateFor(String currency, LocalDate taxableDate, BigDecimal manualRate) {
        if ("CZK".equalsIgnoreCase(currency)) {
            return null;
        }
        if (manualRate != null) {
            if (manualRate.signum() <= 0) {
                throw new IllegalArgumentException("The exchange rate must be positive");
            }
            return new ExchangeRates.Rate(currency, manualRate, taxableDate, "manual");
        }
        try {
            return rates.rate(currency, taxableDate);
        } catch (RatesUnavailableException e) {
            throw new IllegalStateException("The ČNB rate for " + currency + " is not available; enter the rate by hand", e);
        }
    }

    private void freeze(Invoice invoice, ExchangeRates.Rate rate, String creditOfNumber) {
        LocalDate issueDate = today();
        String seriesId = InvoiceSeries.id(invoice.getKind(), issueDate.getYear());
        series.ensure(seriesId);
        int seq = series.lock(seriesId).orElseThrow().next();
        String number = invoice.getKind().number(issueDate.getYear(), seq);
        String symbol = invoice.getKind().variableSymbol(issueDate.getYear(), seq);

        DealSummary deal = deal(invoice.getDealId());
        CounterpartySummary customer = customer(deal);
        BigDecimal czk = rate == null ? null : rate.czkPerUnit();
        String qr = paymentQr(invoice, number, symbol);
        InvoiceDocument doc = new InvoiceDocument(invoice.getKind(), number, creditOfNumber, symbol, invoice.getLanguage(),
                issueDate, invoice.getTaxableDate(), invoice.getDueDate(), supplier(), party(customer), deal.number(),
                deal.title(), invoice.getCurrency(),
                invoice.getLines().stream().map(l -> new InvoiceDocument.Line(l.getDescription(), l.getUnit(), l.getQuantity(),
                        l.getUnitPrice(), l.getTotal())).toList(),
                invoice.getVatMode(), invoice.getVatRate(), invoice.getSubtotal(), invoice.getVatAmount(), invoice.getTotal(),
                czk, rate == null ? null : rate.validFor(),
                czk == null ? null : invoice.getSubtotal().multiply(czk).setScale(2, RoundingMode.HALF_UP),
                czk == null ? null : invoice.getVatAmount().multiply(czk).setScale(2, RoundingMode.HALF_UP),
                VatNotes.note(invoice.getLanguage(), invoice.getVatMode(), suggestion(deal, customer).mode() == invoice.getVatMode()
                        ? suggestion(deal, customer).reason() : null),
                invoice.getNote(), bank(), qr, actor.name());
        byte[] bytes = pdf.render(InvoiceHtml.render(doc, pdf.logo(), qr == null ? null : QrImage.dataUri(qr), PdfRenderer.FONT));
        String sha = sha256(bytes);
        invoice.issue(number, symbol, issueDate, czk, rate == null ? null : rate.validFor(), toJson(doc), bytes, sha,
                clock.instant(), actor.name());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("number", number);
        payload.put("kind", invoice.getKind());
        payload.put("total", invoice.getTotal());
        payload.put("currency", invoice.getCurrency());
        payload.put("vatMode", invoice.getVatMode());
        payload.put("rate", czk);
        payload.put("rateSource", rate == null ? null : rate.source());
        payload.put("pdfSha256", sha);
        audit.record(AUDIT_TYPE, invoice.getId().toString(), "ISSUED", payload);
    }

    /** EPC (SEPA) for EUR, Czech QR Platba for CZK; none for other currencies or credit notes. */
    private String paymentQr(Invoice invoice, String number, String symbol) {
        if (invoice.getKind() != InvoiceKind.INVOICE || company.iban() == null || company.iban().isBlank()) {
            return null;
        }
        return switch (invoice.getCurrency()) {
            case "EUR" -> company.swift() == null || company.swift().isBlank() ? null
                    : PaymentQr.epc(company.swift(), company.name(), company.iban(), invoice.getTotal(), number);
            case "CZK" -> PaymentQr.spayd(company.iban(), invoice.getTotal(), "CZK", symbol, number);
            default -> null;
        };
    }

    private VatAdvisor.Suggestion suggestion(DealSummary deal, CounterpartySummary customer) {
        return VatAdvisor.suggest(customer.country(), customer.vatNumber(), deal.siteCountry(),
                "CONSTRUCTION".equals(deal.direction()));
    }

    public VatAdvisor.Suggestion suggestion(UUID dealId) {
        DealSummary deal = deal(dealId);
        return suggestion(deal, customer(deal));
    }

    private AcceptedStage stageOf(UUID dealId, UUID stageId) {
        if (stageId == null) {
            throw new IllegalArgumentException("Choose the accepted stage to invoice");
        }
        AcceptedStage stage = sites.acceptedStage(stageId)
                .orElseThrow(() -> new IllegalArgumentException("Only an accepted stage can be invoiced"));
        if (!stage.dealId().equals(dealId)) {
            throw new IllegalArgumentException("The stage belongs to another deal");
        }
        return stage;
    }

    static String stageLine(AcceptedStage s) {
        return "Etapa " + s.seq() + ": " + s.title();
    }

    private DealSummary deal(UUID id) {
        return deals.find(id).orElseThrow(() -> new NotFoundException("Deal " + id + " not found"));
    }

    private CounterpartySummary customer(DealSummary deal) {
        return counterparties.find(deal.customerId())
                .orElseThrow(() -> new IllegalStateException("Customer of deal " + deal.number() + " is missing"));
    }

    private InvoiceDocument.Party supplier() {
        return new InvoiceDocument.Party(company.name(), company.country(), blank(company.companyId()),
                blank(company.vatNumber()), blank(company.address()), blank(company.email()), blank(company.phone()));
    }

    private static InvoiceDocument.Party party(CounterpartySummary c) {
        return new InvoiceDocument.Party(c.legalName(), c.country(), c.companyId(), c.vatNumber(), c.address(), null, null);
    }

    private InvoiceDocument.Bank bank() {
        return company.iban() == null || company.iban().isBlank() ? null
                : new InvoiceDocument.Bank(blank(company.bankName()), company.iban(), blank(company.swift()));
    }

    private String toJson(InvoiceDocument d) {
        try {
            return json.writeValueAsString(d);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
