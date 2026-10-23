package com.aleruan.dealos.deal.api;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.application.ChangeOrderService;
import com.aleruan.dealos.deal.application.DealService;
import com.aleruan.dealos.deal.domain.ChangeOrder;
import com.aleruan.dealos.deal.domain.ChangeOrderStatus;
import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealPricing;
import com.aleruan.dealos.deal.domain.LineKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class ChangeOrderController {

    record DraftRequest(@NotBlank String title, String reason, Integer extraDays) {
        ChangeOrderService.Draft draft() {
            return new ChangeOrderService.Draft(title, reason, extraDays);
        }
    }

    /** Like a deal line, but the quantity may be negative when work is taken out of the scope. */
    record LineRequest(@NotNull LineKind kind, @NotBlank String description, String workType, String unit,
                       @NotNull BigDecimal quantity, @NotNull @DecimalMin("0") BigDecimal unitCost, BigDecimal markupPct,
                       @DecimalMin("0") BigDecimal fixedUnitPrice, UUID subcontractorId) {
        DealService.LineData data() {
            return new DealService.LineData(kind, description, workType, unit, quantity, unitCost, markupPct,
                    fixedUnitPrice, subcontractorId);
        }
    }

    record DecisionRequest(@NotBlank String by, String note) {
    }

    record ChangeOrderView(UUID id, UUID dealId, String number, int seq, String title, String reason,
                           ChangeOrderStatus status, Set<ChangeOrderStatus> nextStatuses, boolean pricesEditable,
                           int extraDays, Instant sentAt, Instant decidedAt, String decidedBy, String decisionNote,
                           Instant createdAt, String createdBy, List<DealController.LineView> lines,
                           DealPricing.Totals totals) {
    }

    private final ChangeOrderService service;
    private final DealService deals;
    private final CounterpartyLookup counterparties;

    ChangeOrderController(ChangeOrderService service, DealService deals, CounterpartyLookup counterparties) {
        this.service = service;
        this.deals = deals;
        this.counterparties = counterparties;
    }

    @GetMapping("/deals/{dealId}/change-orders")
    List<ChangeOrderView> list(@PathVariable UUID dealId) {
        Deal deal = deals.get(dealId);
        Map<UUID, CounterpartySummary> cache = new HashMap<>();
        return service.list(dealId).stream().map(co -> view(co, deal, cache)).toList();
    }

    @PostMapping("/deals/{dealId}/change-orders")
    @ResponseStatus(HttpStatus.CREATED)
    ChangeOrderView create(@PathVariable UUID dealId, @Valid @RequestBody DraftRequest r) {
        return view(service.create(dealId, r.draft()));
    }

    @GetMapping("/change-orders/{id}")
    ChangeOrderView get(@PathVariable UUID id) {
        return view(service.get(id));
    }

    @PutMapping("/change-orders/{id}")
    ChangeOrderView edit(@PathVariable UUID id, @Valid @RequestBody DraftRequest r) {
        return view(service.edit(id, r.draft()));
    }

    @PostMapping("/change-orders/{id}/lines")
    ChangeOrderView addLine(@PathVariable UUID id, @Valid @RequestBody LineRequest r) {
        return view(service.addLine(id, r.data()));
    }

    @PutMapping("/change-orders/{id}/lines/{lineId}")
    ChangeOrderView updateLine(@PathVariable UUID id, @PathVariable UUID lineId, @Valid @RequestBody LineRequest r) {
        return view(service.updateLine(id, lineId, r.data()));
    }

    @DeleteMapping("/change-orders/{id}/lines/{lineId}")
    ChangeOrderView removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        return view(service.removeLine(id, lineId));
    }

    @PostMapping("/change-orders/{id}/send")
    ChangeOrderView send(@PathVariable UUID id) {
        return view(service.send(id));
    }

    @PostMapping("/change-orders/{id}/revise")
    ChangeOrderView revise(@PathVariable UUID id) {
        return view(service.revise(id));
    }

    @PostMapping("/change-orders/{id}/cancel")
    ChangeOrderView cancel(@PathVariable UUID id) {
        return view(service.cancel(id));
    }

    @PostMapping("/change-orders/{id}/approve")
    ChangeOrderView approve(@PathVariable UUID id, @Valid @RequestBody DecisionRequest r) {
        return view(service.approve(id, new ChangeOrderService.Decision(r.by(), r.note())));
    }

    @PostMapping("/change-orders/{id}/reject")
    ChangeOrderView reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest r) {
        return view(service.reject(id, new ChangeOrderService.Decision(r.by(), r.note())));
    }

    private ChangeOrderView view(ChangeOrder co) {
        return view(co, deals.get(co.getDealId()), new HashMap<>());
    }

    private ChangeOrderView view(ChangeOrder co, Deal deal, Map<UUID, CounterpartySummary> cache) {
        BigDecimal markup = deal.getDefaultMarkupPct();
        List<DealController.LineView> lines = co.getLines().stream().map(l -> {
            DealPricing.PricedLine p = DealPricing.price(l.pricingInput(), markup);
            CounterpartySummary sub = l.getSubcontractorId() == null ? null
                    : cache.computeIfAbsent(l.getSubcontractorId(), k -> counterparties.find(k).orElse(null));
            return new DealController.LineView(l.getId(), l.getPosition(), l.getKind(), l.getDescription(),
                    l.getWorkType(), l.getUnit(), l.getQuantity(), l.getUnitCost(), p.markupPct(),
                    l.getMarkupPct() == null && l.getFixedUnitPrice() == null, l.getFixedUnitPrice(), p.unitPrice(),
                    p.costTotal(), p.priceTotal(), p.margin(), DealController.PartyView.of(sub));
        }).toList();
        return new ChangeOrderView(co.getId(), co.getDealId(), co.number(deal.getNumber()), co.getSeq(), co.getTitle(),
                co.getReason(), co.getStatus(), co.getStatus().next(), co.getStatus().allowsPriceChanges(),
                co.getExtraDays(), co.getSentAt(), co.getDecidedAt(), co.getDecidedBy(), co.getDecisionNote(),
                co.getCreatedAt(), co.getCreatedBy(), lines, co.totals(markup));
    }
}
