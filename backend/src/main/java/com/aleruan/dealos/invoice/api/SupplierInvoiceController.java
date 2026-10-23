package com.aleruan.dealos.invoice.api;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.invoice.application.SupplierInvoiceService;
import com.aleruan.dealos.invoice.domain.SupplierInvoice;
import com.aleruan.dealos.platform.files.FileStore;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supplier-invoices")
class SupplierInvoiceController {

    record PartyView(UUID id, String legalName, String country, String checkStatus) {
    }

    record SupplierInvoiceView(UUID id, PartyView supplier, UUID dealId, String dealNumber, String number,
                               String variableSymbol, LocalDate issueDate, LocalDate taxableDate, LocalDate dueDate,
                               String currency, BigDecimal netAmount, BigDecimal vatAmount, BigDecimal total,
                               String vatNote, String bankAccount, String accountCheck, boolean hasFile,
                               String fileSha256, UUID importId, LocalDate paidOn, boolean overdue, Instant createdAt,
                               String createdBy) {
    }

    record DealRequest(UUID dealId) {
    }

    record PaidRequest(LocalDate paidOn) {
    }

    private final SupplierInvoiceService service;
    private final CounterpartyLookup counterparties;
    private final DealCatalog deals;

    SupplierInvoiceController(SupplierInvoiceService service, CounterpartyLookup counterparties, DealCatalog deals) {
        this.service = service;
        this.counterparties = counterparties;
        this.deals = deals;
    }

    @GetMapping
    List<SupplierInvoiceView> list(@RequestParam(required = false) UUID dealId,
                                   @RequestParam(required = false) UUID supplierId) {
        Map<UUID, PartyView> parties = new HashMap<>();
        Map<UUID, String> numbers = new HashMap<>();
        return service.search(dealId, supplierId).stream().map(i -> view(i, parties, numbers)).toList();
    }

    @GetMapping("/{id}")
    SupplierInvoiceView get(@PathVariable UUID id) {
        return view(service.get(id), new HashMap<>(), new HashMap<>());
    }

    @PutMapping("/{id}/deal")
    SupplierInvoiceView linkDeal(@PathVariable UUID id, @RequestBody DealRequest r) {
        return view(service.linkDeal(id, r.dealId()), new HashMap<>(), new HashMap<>());
    }

    @PostMapping("/{id}/paid")
    SupplierInvoiceView paid(@PathVariable UUID id, @RequestBody PaidRequest r) {
        return view(service.markPaid(id, r.paidOn()), new HashMap<>(), new HashMap<>());
    }

    @GetMapping("/{id}/file")
    ResponseEntity<byte[]> file(@PathVariable UUID id) {
        FileStore.Content c = service.file(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.info().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(c.info().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'")
                .body(c.data());
    }

    private SupplierInvoiceView view(SupplierInvoice i, Map<UUID, PartyView> parties, Map<UUID, String> numbers) {
        PartyView supplier = parties.computeIfAbsent(i.getSupplierId(), id -> counterparties.find(id)
                .map(SupplierInvoiceController::party).orElse(new PartyView(id, id.toString(), null, null)));
        String dealNumber = i.getDealId() == null ? null : numbers.computeIfAbsent(i.getDealId(),
                id -> deals.find(id).map(DealCatalog.DealSummary::number).orElse(null));
        return new SupplierInvoiceView(i.getId(), supplier, i.getDealId(), dealNumber, i.getNumber(),
                i.getVariableSymbol(), i.getIssueDate(), i.getTaxableDate(), i.getDueDate(), i.getCurrency(),
                i.getNetAmount(), i.getVatAmount(), i.getTotal(), i.getVatNote(), i.getBankAccount(), i.getAccountCheck(),
                i.getFileId() != null, i.getFileSha256(), i.getImportId(), i.getPaidOn(), i.isOverdue(service.today()),
                i.getCreatedAt(), i.getCreatedBy());
    }

    private static PartyView party(CounterpartySummary c) {
        return new PartyView(c.id(), c.legalName(), c.country(), c.checkStatus());
    }
}
