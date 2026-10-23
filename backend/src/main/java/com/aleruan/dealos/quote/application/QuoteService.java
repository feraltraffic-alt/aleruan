package com.aleruan.dealos.quote.application;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.deal.DealCatalog.CustomerLine;
import com.aleruan.dealos.deal.DealCatalog.DealSummary;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.NotFoundException;
import com.aleruan.dealos.quote.domain.Quote;
import com.aleruan.dealos.quote.domain.QuoteDocument;
import com.aleruan.dealos.quote.domain.QuoteLanguage;
import com.aleruan.dealos.quote.domain.QuoteRepository;
import com.aleruan.dealos.quote.domain.QuoteSummary;
import com.aleruan.dealos.quote.domain.QuoteTexts;
import com.aleruan.dealos.quote.domain.VatAdvisor;
import com.aleruan.dealos.quote.domain.VatMode;
import com.aleruan.dealos.quote.pdf.QuotePdfRenderer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteService {

    public static final String AUDIT_TYPE = "Quote";
    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    /** What the manager chooses when issuing; nulls fall back to the suggestion. */
    public record IssueRequest(QuoteLanguage language, Integer validDays, VatMode vatMode, BigDecimal vatRate,
                               String terms) {
    }

    /** Defaults for the issue form: language, VAT treatment and why. */
    public record Defaults(QuoteLanguage language, VatMode vatMode, BigDecimal vatRate, VatAdvisor.Reason reason,
                           int validDays) {
    }

    private final QuoteRepository quotes;
    private final DealCatalog deals;
    private final CounterpartyLookup counterparties;
    private final QuotePdfRenderer renderer;
    private final CompanyProfile company;
    private final AuditService audit;
    private final CurrentActor actor;
    private final ObjectMapper json;
    private final Clock clock;
    private final int defaultValidDays;

    QuoteService(QuoteRepository quotes, DealCatalog deals, CounterpartyLookup counterparties,
                 QuotePdfRenderer renderer, CompanyProfile company, AuditService audit, CurrentActor actor,
                 ObjectMapper json, Clock clock, @Value("${dealos.quotes.valid-days:30}") int defaultValidDays) {
        this.quotes = quotes;
        this.deals = deals;
        this.counterparties = counterparties;
        this.renderer = renderer;
        this.company = company;
        this.audit = audit;
        this.actor = actor;
        this.json = json;
        this.clock = clock;
        this.defaultValidDays = defaultValidDays;
    }

    @Transactional(readOnly = true)
    public Defaults defaults(UUID dealId) {
        DealSummary deal = deal(dealId);
        CounterpartySummary customer = customer(deal);
        VatAdvisor.Suggestion s = suggestion(deal, customer);
        return new Defaults(QuoteLanguage.forCountry(customer.country()), s.mode(), s.rate(), s.reason(), defaultValidDays);
    }

    @Transactional(readOnly = true)
    public List<QuoteSummary> list(UUID dealId) {
        deal(dealId);
        return quotes.summaries(dealId);
    }

    @Transactional(readOnly = true)
    public Quote get(UUID id) {
        return quotes.findById(id).orElseThrow(() -> new NotFoundException("Quote " + id + " not found"));
    }

    /** Freezes the deal's current customer prices into a new quote version and its PDF. */
    @Transactional
    public Quote issue(UUID dealId, IssueRequest request) {
        DealSummary deal = deal(dealId);
        CounterpartySummary customer = customer(deal);
        List<CustomerLine> lines = deals.customerLines(dealId);
        if (lines.isEmpty()) {
            throw new IllegalStateException("Deal " + deal.number() + " has no lines to quote");
        }
        deals.markQuoted(dealId); // fails once the customer accepted: prices are frozen then

        VatAdvisor.Suggestion suggestion = suggestion(deal, customer);
        VatMode mode = request.vatMode() != null ? request.vatMode() : suggestion.mode();
        BigDecimal rate = !mode.chargesVat() ? BigDecimal.ZERO
                : request.vatRate() != null ? request.vatRate()
                : mode == suggestion.mode() ? suggestion.rate() : VatAdvisor.CZ_STANDARD_RATE;
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalArgumentException("VAT rate must be between 0 and 100");
        }
        QuoteLanguage language = request.language() != null ? request.language() : QuoteLanguage.forCountry(customer.country());
        int validDays = request.validDays() != null ? request.validDays() : defaultValidDays;
        if (validDays < 1 || validDays > 365) {
            throw new IllegalArgumentException("A quote is valid for 1 to 365 days");
        }
        // the article in the EU reverse-charge note depends on why it applies, so keep the reason when it matches
        VatAdvisor.Reason reason = mode == suggestion.mode() ? suggestion.reason() : null;

        List<QuoteDocument.Line> docLines = lines.stream()
                .map(l -> new QuoteDocument.Line(l.position(), l.description(), l.unit(), l.quantity(), l.unitPrice(),
                        l.total()))
                .toList();
        QuoteDocument.Amounts amounts = QuoteDocument.amounts(docLines, mode, rate);
        LocalDate today = LocalDate.now(clock.withZone(PRAGUE));
        int version = quotes.lastVersion(dealId) + 1;

        QuoteDocument document = new QuoteDocument(
                deal.number().replaceFirst("^D-", "Q-"), version, language, today, today.plusDays(validDays),
                actor.name(), supplier(), party(customer), deal.title(), deal.siteCountry(), deal.currency(),
                docLines, mode, rate, amounts.subtotal(), amounts.vatAmount(), amounts.total(),
                QuoteTexts.vatNote(language, mode, reason), blankToNull(request.terms()),
                company.iban() == null || company.iban().isBlank() ? null
                        : new QuoteDocument.Bank(blankToNull(company.bankName()), company.iban(), blankToNull(company.swift())));

        byte[] pdf = renderer.render(document);
        String sha = sha256(pdf);
        Quote quote = quotes.save(new Quote(dealId, document, clock.instant(), toJson(document), pdf, sha));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dealId", dealId);
        payload.put("number", quote.displayNumber());
        payload.put("total", amounts.total());
        payload.put("currency", deal.currency());
        payload.put("vatMode", mode);
        payload.put("vatSuggested", suggestion.mode());
        payload.put("pdfSha256", sha);
        audit.record(AUDIT_TYPE, quote.getId().toString(), "ISSUED", payload);
        return quote;
    }

    /** Browser preview of an issued quote, rendered from the stored document. */
    @Transactional(readOnly = true)
    public String html(UUID id) {
        try {
            return renderer.html(json.readValue(get(id).getDocument(), QuoteDocument.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored quote " + id + " is unreadable", e);
        }
    }

    private VatAdvisor.Suggestion suggestion(DealSummary deal, CounterpartySummary customer) {
        return VatAdvisor.suggest(customer.country(), customer.vatNumber(), deal.siteCountry(),
                "CONSTRUCTION".equals(deal.direction()));
    }

    private DealSummary deal(UUID dealId) {
        return deals.find(dealId).orElseThrow(() -> new NotFoundException("Deal " + dealId + " not found"));
    }

    private CounterpartySummary customer(DealSummary deal) {
        return counterparties.find(deal.customerId())
                .orElseThrow(() -> new IllegalStateException("Customer of deal " + deal.number() + " is missing"));
    }

    private QuoteDocument.Party supplier() {
        return new QuoteDocument.Party(company.name(), company.country(), blankToNull(company.companyId()),
                blankToNull(company.vatNumber()), blankToNull(company.address()), blankToNull(company.email()),
                blankToNull(company.phone()), blankToNull(company.web()));
    }

    private static QuoteDocument.Party party(CounterpartySummary c) {
        return new QuoteDocument.Party(c.legalName(), c.country(), c.companyId(), c.vatNumber(), c.address(),
                null, null, null);
    }

    private String toJson(QuoteDocument d) {
        try {
            return json.writeValueAsString(d);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
