package com.aleruan.dealos.invoice.application;

import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.invoice.SupplierInvoices;
import com.aleruan.dealos.invoice.domain.SupplierInvoice;
import com.aleruan.dealos.invoice.domain.SupplierInvoiceRepository;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.files.FileInfo;
import com.aleruan.dealos.platform.files.FileStore;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.ConflictException;
import com.aleruan.dealos.platform.web.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Invoices received from subcontractors and suppliers: registered with the original file, linked to deals, paid. */
@Service
public class SupplierInvoiceService implements SupplierInvoices {

    public static final String AUDIT_TYPE = "SupplierInvoice";
    public static final String FILE_OWNER = "SupplierInvoice";

    private final SupplierInvoiceRepository invoices;
    private final DealCatalog deals;
    private final FileStore files;
    private final AuditService audit;
    private final CurrentActor actor;
    private final Clock clock;

    SupplierInvoiceService(SupplierInvoiceRepository invoices, DealCatalog deals, FileStore files, AuditService audit,
                           CurrentActor actor, Clock clock) {
        this.invoices = invoices;
        this.deals = deals;
        this.files = files;
        this.audit = audit;
        this.actor = actor;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID supplierId, String number) {
        return number != null && invoices.existsBySupplierIdAndNumberIgnoreCase(supplierId, number.strip());
    }

    @Override
    @Transactional
    public UUID register(NewSupplierInvoice n) {
        if (exists(n.supplierId(), n.number())) {
            throw new ConflictException("Invoice " + n.number() + " of this supplier is already registered");
        }
        requireDeal(n.dealId());
        SupplierInvoice inv = invoices.save(new SupplierInvoice(new SupplierInvoice.Data(n.supplierId(), n.dealId(),
                n.number(), n.variableSymbol(), n.issueDate(), n.taxableDate(), n.dueDate(), n.currency(), n.netAmount(),
                n.vatAmount(), n.total(), n.vatNote(), n.bankAccount(), null), n.importId(), clock.instant(), actor.name()));
        if (n.file() != null) {
            FileInfo f = files.store(FILE_OWNER, inv.getId(), n.fileName(), n.file());
            inv.attachFile(f.id(), f.sha256());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("supplierId", n.supplierId());
        payload.put("number", inv.getNumber());
        payload.put("total", inv.getTotal());
        payload.put("currency", inv.getCurrency());
        payload.put("dealId", n.dealId());
        payload.put("importId", n.importId());
        payload.put("fileSha256", inv.getFileSha256());
        audit.record(AUDIT_TYPE, inv.getId().toString(), "REGISTERED", payload);
        return inv.getId();
    }

    @Override
    @Transactional
    public void recordAccountCheck(UUID id, String status) {
        get(id).recordAccountCheck(status);
    }

    @Transactional(readOnly = true)
    public List<SupplierInvoice> search(UUID dealId, UUID supplierId) {
        return invoices.search(dealId, supplierId);
    }

    @Transactional(readOnly = true)
    public SupplierInvoice get(UUID id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("Supplier invoice " + id + " not found"));
    }

    @Transactional
    public SupplierInvoice linkDeal(UUID id, UUID dealId) {
        requireDeal(dealId);
        SupplierInvoice inv = get(id);
        inv.linkDeal(dealId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dealId", dealId);
        audit.record(AUDIT_TYPE, id.toString(), "DEAL_LINKED", payload);
        return inv;
    }

    @Transactional
    public SupplierInvoice markPaid(UUID id, LocalDate on) {
        SupplierInvoice inv = get(id);
        if (on != null && on.isAfter(today())) {
            throw new IllegalArgumentException("A payment cannot be in the future");
        }
        inv.markPaid(on);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paidOn", on);
        audit.record(AUDIT_TYPE, id.toString(), on == null ? "PAYMENT_CLEARED" : "PAID", payload);
        return inv;
    }

    @Transactional(readOnly = true)
    public FileStore.Content file(UUID id) {
        SupplierInvoice inv = get(id);
        if (inv.getFileId() == null) {
            throw new NotFoundException("Supplier invoice " + inv.getNumber() + " has no file");
        }
        return files.get(inv.getFileId());
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(java.time.ZoneId.of("Europe/Prague")));
    }

    private void requireDeal(UUID dealId) {
        if (dealId != null && deals.find(dealId).isEmpty()) {
            throw new IllegalArgumentException("Deal " + dealId + " does not exist");
        }
    }
}
