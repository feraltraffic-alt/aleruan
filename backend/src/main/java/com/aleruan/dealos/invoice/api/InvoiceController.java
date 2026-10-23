package com.aleruan.dealos.invoice.api;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.invoice.application.InvoiceService;
import com.aleruan.dealos.invoice.domain.Invoice;
import com.aleruan.dealos.invoice.domain.InvoiceKind;
import com.aleruan.dealos.invoice.domain.InvoiceStatus;
import com.aleruan.dealos.platform.documents.DocLanguage;
import com.aleruan.dealos.platform.documents.VatMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class InvoiceController {

    record DraftBody(@NotNull InvoiceService.Source source, UUID stageId) {
    }

    record LineBody(@NotBlank @Size(max = 1000) String description, @Size(max = 20) String unit, @NotNull BigDecimal quantity,
                    @NotNull BigDecimal unitPrice, UUID stageId) {
    }

    record EditBody(DocLanguage language, @NotNull VatMode vatMode, @DecimalMin("0") @DecimalMax("99.99") BigDecimal vatRate,
                    @NotNull LocalDate taxableDate, @NotNull LocalDate dueDate, @Size(max = 2000) String note,
                    @NotNull @Size(max = 200) List<@Valid LineBody> lines) {
    }

    record IssueBody(@DecimalMin(value = "0", inclusive = false) BigDecimal exchangeRate) {
    }

    record PaymentBody(@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount, @NotNull LocalDate paidOn) {
    }

    record CreditBody(@NotBlank @Size(max = 1000) String reason) {
    }

    record LineView(UUID id, int position, String description, String unit, BigDecimal quantity, BigDecimal unitPrice,
                    BigDecimal total, UUID stageId) {
    }

    record PartyView(UUID id, String legalName, String country, String checkStatus) {
    }

    record InvoiceView(UUID id, UUID dealId, String dealNumber, String dealTitle, PartyView customer, InvoiceKind kind,
                       InvoiceStatus status, String number, String variableSymbol, UUID creditOfId, DocLanguage language,
                       String currency, VatMode vatMode, BigDecimal vatRate, LocalDate issueDate, LocalDate taxableDate,
                       LocalDate dueDate, BigDecimal subtotal, BigDecimal vatAmount, BigDecimal total,
                       BigDecimal exchangeRate, LocalDate rateDate, BigDecimal vatAmountCzk, String note,
                       BigDecimal paidAmount, LocalDate paidOn, boolean overdue, String pdfSha256, Instant createdAt,
                       String createdBy, Instant issuedAt, String issuedBy, List<LineView> lines) {
    }

    private final InvoiceService service;
    private final DealCatalog deals;
    private final CounterpartyLookup counterparties;

    InvoiceController(InvoiceService service, DealCatalog deals, CounterpartyLookup counterparties) {
        this.service = service;
        this.deals = deals;
        this.counterparties = counterparties;
    }

    @GetMapping("/invoices")
    List<InvoiceView> search(@RequestParam(required = false) InvoiceStatus status,
                             @RequestParam(required = false) UUID dealId,
                             @RequestParam(required = false) UUID customerId,
                             @RequestParam(defaultValue = "false") boolean overdue) {
        Map<UUID, DealCatalog.DealSummary> d = new HashMap<>();
        Map<UUID, CounterpartySummary> c = new HashMap<>();
        return service.search(new InvoiceService.Filter(status, dealId, customerId, overdue)).stream()
                .map(i -> view(i, d, c)).toList();
    }

    @GetMapping("/invoices/{id}")
    InvoiceView get(@PathVariable UUID id) {
        return view(service.get(id));
    }

    @GetMapping("/deals/{dealId}/invoicing")
    InvoiceService.DealInvoicing invoicing(@PathVariable UUID dealId) {
        return service.dealInvoicing(dealId);
    }

    @PostMapping("/deals/{dealId}/invoices")
    InvoiceView draft(@PathVariable UUID dealId, @Validated @RequestBody DraftBody body) {
        return view(service.createDraft(dealId, body.source(), body.stageId()));
    }

    @PutMapping("/invoices/{id}")
    InvoiceView edit(@PathVariable UUID id, @Validated @RequestBody EditBody b) {
        return view(service.edit(id, new Invoice.Settings(b.language(), b.vatMode(),
                        b.vatRate() == null ? BigDecimal.ZERO : b.vatRate(), b.taxableDate(), b.dueDate(), b.note()),
                b.lines().stream().map(l -> new Invoice.LineData(l.description(), l.unit(), l.quantity(), l.unitPrice(),
                        l.stageId())).toList()));
    }

    @DeleteMapping("/invoices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.deleteDraft(id);
    }

    @PostMapping("/invoices/{id}/issue")
    InvoiceView issue(@PathVariable UUID id, @Validated @RequestBody(required = false) IssueBody body) {
        return view(service.issue(id, body == null ? null : body.exchangeRate()));
    }

    @PostMapping("/invoices/{id}/payments")
    InvoiceView pay(@PathVariable UUID id, @Validated @RequestBody PaymentBody body) {
        return view(service.pay(id, body.amount(), body.paidOn()));
    }

    @PostMapping("/invoices/{id}/credit")
    InvoiceView credit(@PathVariable UUID id, @Validated @RequestBody CreditBody body) {
        return view(service.credit(id, body.reason()));
    }

    @GetMapping("/invoices/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        Invoice i = service.get(id);
        if (i.getPdf() == null) {
            throw new IllegalStateException("A draft has no PDF yet; issue it first");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(i.getNumber() + ".pdf").build().toString())
                .eTag(i.getPdfSha256())
                .body(i.getPdf());
    }

    @GetMapping(value = "/invoices/{id}/html", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    String html(@PathVariable UUID id) {
        return service.html(id);
    }

    private InvoiceView view(Invoice i) {
        return view(i, new HashMap<>(), new HashMap<>());
    }

    private InvoiceView view(Invoice i, Map<UUID, DealCatalog.DealSummary> dealCache, Map<UUID, CounterpartySummary> partyCache) {
        DealCatalog.DealSummary deal = dealCache.computeIfAbsent(i.getDealId(), k -> deals.find(k).orElse(null));
        CounterpartySummary c = partyCache.computeIfAbsent(i.getCustomerId(), k -> counterparties.find(k).orElse(null));
        return new InvoiceView(i.getId(), i.getDealId(), deal == null ? null : deal.number(), deal == null ? null : deal.title(),
                c == null ? null : new PartyView(c.id(), c.legalName(), c.country(), c.checkStatus()), i.getKind(),
                i.getStatus(), i.getNumber(), i.getVariableSymbol(), i.getCreditOfId(), i.getLanguage(), i.getCurrency(),
                i.getVatMode(), i.getVatRate(), i.getIssueDate(), i.getTaxableDate(), i.getDueDate(), i.getSubtotal(),
                i.getVatAmount(), i.getTotal(), i.getExchangeRate(), i.getRateDate(), i.getVatAmountCzk(), i.getNote(),
                i.getPaidAmount(), i.getPaidOn(), i.isOverdue(service.today()), i.getPdfSha256(), i.getCreatedAt(),
                i.getCreatedBy(), i.getIssuedAt(), i.getIssuedBy(),
                i.getLines().stream().map(l -> new LineView(l.getId(), l.getPosition(), l.getDescription(), l.getUnit(),
                        l.getQuantity(), l.getUnitPrice(), l.getTotal(), l.getStageId())).toList());
    }
}
