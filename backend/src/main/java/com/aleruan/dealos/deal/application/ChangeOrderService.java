package com.aleruan.dealos.deal.application;

import com.aleruan.dealos.deal.domain.ChangeOrder;
import com.aleruan.dealos.deal.domain.ChangeOrderRepository;
import com.aleruan.dealos.deal.domain.ChangeOrderStatus;
import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealPricing;
import com.aleruan.dealos.deal.domain.DealStatus;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Extra work on won deals: drafted by the manager, sent, then approved or rejected by the customer. */
@Service
public class ChangeOrderService {

    public static final String AUDIT_TYPE = "ChangeOrder";

    public record Draft(String title, String reason, Integer extraDays) {
    }

    public record Decision(String by, String note) {
    }

    /** The deal's value as agreed: the original lines plus approved change orders. */
    public record Contract(DealPricing.Totals original, DealPricing.Totals approvedChanges,
                           BigDecimal pendingPrice, DealPricing.Totals total, int extraDays) {
    }

    private final ChangeOrderRepository orders;
    private final DealService deals;
    private final AuditService audit;
    private final CurrentActor actor;
    private final Clock clock;

    ChangeOrderService(ChangeOrderRepository orders, DealService deals, AuditService audit, CurrentActor actor, Clock clock) {
        this.orders = orders;
        this.deals = deals;
        this.audit = audit;
        this.actor = actor;
        this.clock = clock;
    }

    @Transactional
    public ChangeOrder create(UUID dealId, Draft d) {
        Deal deal = deals.get(dealId);
        requireAlive(deal);
        ChangeOrder co = orders.save(new ChangeOrder(dealId, orders.lastSeq(dealId) + 1, d.title(), d.reason(),
                d.extraDays() == null ? 0 : d.extraDays(), clock.instant(), actor.name()));
        audit.record(AUDIT_TYPE, co.getId().toString(), "CREATED", Map.of("dealId", dealId, "number",
                co.number(deal.getNumber()), "title", co.getTitle()));
        return co;
    }

    @Transactional
    public ChangeOrder edit(UUID id, Draft d) {
        ChangeOrder co = get(id);
        co.edit(d.title(), d.reason(), d.extraDays() == null ? 0 : d.extraDays());
        audit.record(AUDIT_TYPE, id.toString(), "EDITED", d);
        return co;
    }

    @Transactional
    public ChangeOrder addLine(UUID id, DealService.LineData l) {
        ChangeOrder co = get(id);
        deals.requireSupplier(l.subcontractorId());
        var line = co.addLine(l.kind(), l.description(), l.workType(), l.unit(), l.quantity(), l.unitCost(),
                l.markupPct(), l.fixedUnitPrice(), l.subcontractorId());
        audit.record(AUDIT_TYPE, id.toString(), "LINE_ADDED", Map.of("lineId", line.getId(), "line", l));
        return co;
    }

    @Transactional
    public ChangeOrder updateLine(UUID id, UUID lineId, DealService.LineData l) {
        ChangeOrder co = get(id);
        Deal deal = deals.get(co.getDealId());
        deals.requireSupplier(l.subcontractorId());
        co.updateLine(lineId, l.kind(), l.description(), l.workType(), l.unit(), l.quantity(), l.unitCost(),
                l.markupPct(), l.fixedUnitPrice(), l.subcontractorId(), deal.getDefaultMarkupPct());
        audit.record(AUDIT_TYPE, id.toString(), "LINE_UPDATED", Map.of("lineId", lineId, "line", l));
        return co;
    }

    @Transactional
    public ChangeOrder removeLine(UUID id, UUID lineId) {
        ChangeOrder co = get(id);
        co.removeLine(lineId);
        audit.record(AUDIT_TYPE, id.toString(), "LINE_REMOVED", Map.of("lineId", lineId));
        return co;
    }

    @Transactional
    public ChangeOrder send(UUID id) {
        ChangeOrder co = get(id);
        requireAlive(deals.get(co.getDealId()));
        co.send(clock.instant());
        audit.record(AUDIT_TYPE, id.toString(), "SENT", Map.of("totals", totals(co)));
        return co;
    }

    @Transactional
    public ChangeOrder approve(UUID id, Decision d) {
        ChangeOrder co = get(id);
        requireAlive(deals.get(co.getDealId()));
        co.approve(d.by(), d.note(), clock.instant());
        audit.record(AUDIT_TYPE, id.toString(), "APPROVED", decision(d, co));
        return co;
    }

    @Transactional
    public ChangeOrder reject(UUID id, Decision d) {
        ChangeOrder co = get(id);
        co.reject(d.by(), d.note(), clock.instant());
        audit.record(AUDIT_TYPE, id.toString(), "REJECTED", decision(d, co));
        return co;
    }

    @Transactional
    public ChangeOrder revise(UUID id) {
        ChangeOrder co = get(id);
        co.revise();
        audit.record(AUDIT_TYPE, id.toString(), "REVISED", Map.of());
        return co;
    }

    @Transactional
    public ChangeOrder cancel(UUID id) {
        ChangeOrder co = get(id);
        co.cancel();
        audit.record(AUDIT_TYPE, id.toString(), "CANCELLED", Map.of());
        return co;
    }

    @Transactional(readOnly = true)
    public ChangeOrder get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Change order " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<ChangeOrder> list(UUID dealId) {
        return orders.findByDealIdOrderBySeq(dealId);
    }

    public DealPricing.Totals totals(ChangeOrder co) {
        return co.totals(deals.get(co.getDealId()).getDefaultMarkupPct());
    }

    @Transactional(readOnly = true)
    public Contract contract(Deal deal) {
        return contractOf(deal, orders.findByDealIdOrderBySeq(deal.getId()));
    }

    /** Original lines plus approved change orders; sent ones are reported as pending. */
    static Contract contractOf(Deal deal, List<ChangeOrder> changeOrders) {
        List<DealPricing.PricedLine> approved = new ArrayList<>();
        BigDecimal pending = BigDecimal.ZERO.setScale(2);
        int days = 0;
        List<DealPricing.PricedLine> all = new ArrayList<>(deal.getLines().stream()
                .map(l -> DealPricing.price(l.pricingInput(), deal.getDefaultMarkupPct())).toList());
        for (ChangeOrder co : changeOrders) {
            List<DealPricing.PricedLine> priced = co.getLines().stream()
                    .map(l -> DealPricing.price(l.pricingInput(), deal.getDefaultMarkupPct())).toList();
            if (co.getStatus() == ChangeOrderStatus.APPROVED) {
                approved.addAll(priced);
                all.addAll(priced);
                days += co.getExtraDays();
            } else if (co.getStatus() == ChangeOrderStatus.SENT) {
                pending = pending.add(DealPricing.total(priced).price());
            }
        }
        return new Contract(deal.totals(), DealPricing.total(approved), pending, DealPricing.total(all), days);
    }

    private static Map<String, Object> decision(Decision d, ChangeOrder co) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("by", d.by());
        m.put("note", d.note());
        m.put("seq", co.getSeq());
        return m;
    }

    private static void requireAlive(Deal deal) {
        if (deal.getStatus() != DealStatus.WON && deal.getStatus() != DealStatus.IN_PROGRESS) {
            throw new IllegalStateException("Change orders are for deals the customer accepted and that are not closed; "
                    + deal.getNumber() + " is " + deal.getStatus());
        }
    }
}
