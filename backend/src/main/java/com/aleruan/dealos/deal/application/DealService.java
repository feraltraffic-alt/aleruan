package com.aleruan.dealos.deal.application;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.deal.domain.ChangeOrder;
import com.aleruan.dealos.deal.domain.ChangeOrderRepository;
import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealLine;
import com.aleruan.dealos.deal.domain.DealRepository;
import com.aleruan.dealos.deal.domain.DealPricing;
import com.aleruan.dealos.deal.domain.DealStatus;
import com.aleruan.dealos.deal.domain.Direction;
import com.aleruan.dealos.deal.domain.LineKind;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DealService implements DealCatalog {

    public static final String AUDIT_TYPE = "Deal";

    public record NewDeal(String title, UUID customerId, Direction direction, String siteCountry, String currency,
                          BigDecimal defaultMarkupPct) {
    }

    public record LineData(LineKind kind, String description, String workType, String unit, BigDecimal quantity,
                           BigDecimal unitCost, BigDecimal markupPct, BigDecimal fixedUnitPrice, UUID subcontractorId) {
    }

    private final DealRepository deals;
    private final ChangeOrderRepository changeOrders;
    private final CounterpartyLookup counterparties;
    private final AuditService audit;
    private final CurrentActor actor;
    private final Clock clock;
    private final com.aleruan.dealos.platform.files.FileStore files;

    DealService(DealRepository deals, ChangeOrderRepository changeOrders, CounterpartyLookup counterparties,
                AuditService audit, CurrentActor actor, Clock clock, com.aleruan.dealos.platform.files.FileStore files) {
        this.files = files;
        this.deals = deals;
        this.changeOrders = changeOrders;
        this.counterparties = counterparties;
        this.audit = audit;
        this.actor = actor;
        this.clock = clock;
    }

    @Transactional
    public Deal create(NewDeal input) {
        CounterpartySummary customer = counterparties.find(input.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer " + input.customerId() + " does not exist"));
        if (!customer.hasRole("CUSTOMER")) {
            throw new IllegalArgumentException(customer.legalName() + " is not marked as a customer");
        }
        int year = Year.now(clock.withZone(ZoneId.of("Europe/Prague"))).getValue();
        String number = "D-%d-%04d".formatted(year, deals.nextNumber());
        Deal deal = deals.save(new Deal(number, input.title().trim(), customer.id(), input.direction(),
                upper(input.siteCountry()), input.currency(), input.defaultMarkupPct(), actor.name(), clock.instant()));
        audit.record(AUDIT_TYPE, deal.getId().toString(), "CREATED", input);
        return deal;
    }

    @Transactional
    public Deal edit(UUID id, String title, BigDecimal defaultMarkupPct, String siteCountry, String notes) {
        Deal deal = get(id);
        deal.edit(title, defaultMarkupPct, upper(siteCountry), notes);
        audit.record(AUDIT_TYPE, id.toString(), "EDITED", Map.of(
                "title", deal.getTitle(), "defaultMarkupPct", deal.getDefaultMarkupPct()));
        return deal;
    }

    @Transactional
    public Deal addLine(UUID id, LineData l) {
        Deal deal = get(id);
        requireSupplier(l.subcontractorId());
        DealLine line = deal.addLine(l.kind(), l.description().trim(), l.workType(), l.unit(), l.quantity(),
                l.unitCost(), l.markupPct(), l.fixedUnitPrice(), l.subcontractorId());
        audit.record(AUDIT_TYPE, id.toString(), "LINE_ADDED", Map.of("lineId", line.getId(), "line", l));
        return deal;
    }

    @Transactional
    public Deal updateLine(UUID id, UUID lineId, LineData l) {
        Deal deal = get(id);
        requireSupplier(l.subcontractorId());
        deal.updateLine(lineId, l.kind(), l.description().trim(), l.workType(), l.unit(), l.quantity(), l.unitCost(),
                l.markupPct(), l.fixedUnitPrice(), l.subcontractorId());
        audit.record(AUDIT_TYPE, id.toString(), "LINE_UPDATED", Map.of("lineId", lineId, "line", l));
        return deal;
    }

    @Transactional
    public Deal removeLine(UUID id, UUID lineId) {
        Deal deal = get(id);
        deal.removeLine(lineId);
        audit.record(AUDIT_TYPE, id.toString(), "LINE_REMOVED", Map.of("lineId", lineId));
        return deal;
    }

    @Transactional
    public Deal moveTo(UUID id, DealStatus target) {
        Deal deal = get(id);
        DealStatus from = deal.getStatus();
        deal.moveTo(target, clock.instant());
        audit.record(AUDIT_TYPE, id.toString(), "STATUS_CHANGED", Map.of("from", from, "to", target,
                "totals", deal.totals()));
        return deal;
    }

    @Transactional(readOnly = true)
    public Deal get(UUID id) {
        return deals.findById(id).orElseThrow(() -> new NotFoundException("Deal " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Deal> search(String q, DealStatus status, UUID customerId) {
        return deals.search(q == null || q.isBlank() ? null : q.trim(), status, customerId);
    }

    // ---- DealCatalog (public module API) ----

    @Override
    @Transactional(readOnly = true)
    public Optional<DealSummary> find(UUID dealId) {
        return deals.findById(dealId).map(d -> new DealSummary(d.getId(), d.getNumber(), d.getTitle(),
                d.getCurrency(), d.getStatus().name(), d.getSiteCountry(), d.getCustomerId(), d.getDirection().name()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChangeOrderSummary> changeOrder(UUID changeOrderId) {
        return changeOrders.findById(changeOrderId).map(co -> summary(get(co.getDealId()), co));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChangeOrderSummary> changeOrders(UUID dealId) {
        Deal deal = get(dealId);
        return changeOrders.findByDealIdOrderBySeq(dealId).stream().map(co -> summary(deal, co)).toList();
    }

    private static ChangeOrderSummary summary(Deal deal, ChangeOrder co) {
        List<CustomerLine> lines = co.getLines().stream().map(l -> {
            DealPricing.PricedLine p = DealPricing.price(l.pricingInput(), deal.getDefaultMarkupPct());
            return new CustomerLine(l.getPosition(), l.getKind().name(), l.getDescription(), l.getWorkType(),
                    l.getUnit(), l.getQuantity(), p.unitPrice(), p.priceTotal());
        }).toList();
        return new ChangeOrderSummary(co.getId(), deal.getId(), co.number(deal.getNumber()), co.getSeq(),
                co.getTitle(), co.getReason(), co.getStatus().name(), co.getExtraDays(), lines,
                co.totals(deal.getDefaultMarkupPct()).price(), co.getDecidedAt(), co.getDecidedBy(),
                co.getSentAt(), co.getCreatedBy());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DealEconomics> economics(java.util.Set<String> statuses) {
        List<DealStatus> wanted = statuses.stream().map(DealStatus::valueOf).toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Deal> list = deals.withStatus(wanted);
        Map<UUID, List<ChangeOrder>> orders = list.isEmpty() ? Map.of()
                : changeOrders.findByDealIdInOrderBySeq(list.stream().map(Deal::getId).toList()).stream()
                        .collect(java.util.stream.Collectors.groupingBy(ChangeOrder::getDealId));
        return list.stream().map(d -> economics(d, orders.getOrDefault(d.getId(), List.of()))).toList();
    }

    private static DealEconomics economics(Deal d, List<ChangeOrder> orders) {
        BigDecimal markup = d.getDefaultMarkupPct();
        List<EconomicsLine> lines = new java.util.ArrayList<>();
        for (DealLine l : d.getLines()) {
            DealPricing.PricedLine p = DealPricing.price(l.pricingInput(), markup);
            lines.add(new EconomicsLine(l.getKind().name(), l.getWorkType(), l.getSubcontractorId(), p.costTotal(),
                    p.priceTotal(), false));
        }
        int pending = 0;
        BigDecimal pendingPrice = BigDecimal.ZERO.setScale(2);
        for (ChangeOrder co : orders) {
            if (co.getStatus() == com.aleruan.dealos.deal.domain.ChangeOrderStatus.APPROVED) {
                co.getLines().forEach(l -> {
                    DealPricing.PricedLine p = DealPricing.price(l.pricingInput(), markup);
                    lines.add(new EconomicsLine(l.getKind().name(), l.getWorkType(), l.getSubcontractorId(),
                            p.costTotal(), p.priceTotal(), true));
                });
            } else if (co.getStatus() == com.aleruan.dealos.deal.domain.ChangeOrderStatus.SENT) {
                pending++;
                pendingPrice = pendingPrice.add(co.totals(markup).price());
            }
        }
        return new DealEconomics(d.getId(), d.getNumber(), d.getTitle(), d.getStatus().name(), d.getDirection().name(),
                d.getSiteCountry(), d.getCurrency(), d.getCustomerId(), d.getCreatedAt(), d.getWonAt(),
                d.getStatusChangedAt(), List.copyOf(lines), pending, pendingPrice);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal contractValue(UUID dealId) {
        Deal deal = get(dealId);
        return ChangeOrderService.contractOf(deal, changeOrders.findByDealIdOrderBySeq(dealId)).total().price();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerLine> customerLines(UUID dealId) {
        Deal deal = get(dealId);
        return deal.getLines().stream().map(l -> {
            DealPricing.PricedLine p = DealPricing.price(l.pricingInput(), deal.getDefaultMarkupPct());
            return new CustomerLine(l.getPosition(), l.getKind().name(), l.getDescription(), l.getWorkType(),
                    l.getUnit(), l.getQuantity(), p.unitPrice(), p.priceTotal());
        }).toList();
    }

    @Override
    @Transactional
    public void markQuoted(UUID dealId) {
        Deal deal = get(dealId);
        if (deal.getStatus() == DealStatus.QUOTED) {
            return;
        }
        if (deal.getStatus() != DealStatus.DRAFT) {
            throw new IllegalStateException("Deal " + deal.getNumber() + " is " + deal.getStatus()
                    + "; a new quote is possible only before the customer accepts");
        }
        moveTo(dealId, DealStatus.QUOTED);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LineSummary> lines(UUID dealId) {
        return get(dealId).getLines().stream()
                .map(l -> new LineSummary(l.getId(), l.getPosition(), l.getKind().name(), l.getDescription(),
                        l.getWorkType(), l.getUnit(), l.getQuantity(), l.getUnitCost(), l.getSubcontractorId()))
                .toList();
    }

    @Override
    @Transactional
    public UUID attachDocument(UUID dealId, String fileName, byte[] data) {
        get(dealId);
        return files.store(DealDocuments.OWNER, dealId, fileName, data).id();
    }

    @Override
    @Transactional
    public void assignCost(UUID dealId, UUID lineId, BigDecimal unitCost, UUID subcontractorId, String reason) {
        Deal deal = get(dealId);
        requireSupplier(subcontractorId);
        deal.assignCost(lineId, unitCost, subcontractorId);
        audit.record(AUDIT_TYPE, dealId.toString(), "COST_ASSIGNED", Map.of("lineId", lineId, "unitCost", unitCost,
                "subcontractorId", subcontractorId, "reason", reason));
    }

    /** Line suppliers must be subcontractors, suppliers or carriers. */
    void requireSupplier(UUID counterpartyId) {
        if (counterpartyId == null) {
            return;
        }
        CounterpartySummary c = counterparties.find(counterpartyId)
                .orElseThrow(() -> new IllegalArgumentException("Counterparty " + counterpartyId + " does not exist"));
        if (!c.hasRole("SUBCONTRACTOR") && !c.hasRole("SUPPLIER") && !c.hasRole("CARRIER")) {
            throw new IllegalArgumentException(c.legalName() + " is not a subcontractor, supplier or carrier");
        }
    }

    private static String upper(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }
}
