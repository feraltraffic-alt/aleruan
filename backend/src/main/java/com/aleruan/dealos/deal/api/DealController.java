package com.aleruan.dealos.deal.api;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.application.ChangeOrderService;
import com.aleruan.dealos.deal.application.DealService;
import com.aleruan.dealos.deal.domain.Deal;
import com.aleruan.dealos.deal.domain.DealLine;
import com.aleruan.dealos.deal.domain.DealPricing;
import com.aleruan.dealos.deal.domain.DealStatus;
import com.aleruan.dealos.deal.domain.Direction;
import com.aleruan.dealos.deal.domain.LineKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/deals")
class DealController {

    record CreateRequest(@NotBlank String title, @NotNull UUID customerId, @NotNull Direction direction,
                         @Pattern(regexp = "[A-Za-z]{2}") String siteCountry,
                         @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
                         @NotNull @DecimalMin(value = "-99.99") BigDecimal defaultMarkupPct) {
    }

    record EditRequest(String title, @DecimalMin(value = "-99.99") BigDecimal defaultMarkupPct, String siteCountry,
                       String notes) {
    }

    record LineRequest(@NotNull LineKind kind, @NotBlank String description, String workType, String unit,
                       @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
                       @NotNull @DecimalMin("0") BigDecimal unitCost, BigDecimal markupPct,
                       @DecimalMin("0") BigDecimal fixedUnitPrice, UUID subcontractorId) {
        DealService.LineData data() {
            return new DealService.LineData(kind, description, workType, unit, quantity, unitCost, markupPct,
                    fixedUnitPrice, subcontractorId);
        }
    }

    record StatusRequest(@NotNull DealStatus status) {
    }

    record PartyView(UUID id, String legalName, String checkStatus) {
        static PartyView of(CounterpartySummary c) {
            return c == null ? null : new PartyView(c.id(), c.legalName(), c.checkStatus());
        }
    }

    record LineView(UUID id, int position, LineKind kind, String description, String workType, String unit,
                    BigDecimal quantity, BigDecimal unitCost, BigDecimal markupPct, boolean markupFromDeal,
                    BigDecimal fixedUnitPrice, BigDecimal unitPrice, BigDecimal costTotal, BigDecimal priceTotal,
                    BigDecimal margin, PartyView subcontractor) {
    }

    record DealView(UUID id, String number, String title, DealStatus status, Set<DealStatus> nextStatuses,
                    Direction direction, String siteCountry, String currency, BigDecimal defaultMarkupPct,
                    boolean pricesEditable, PartyView customer, String manager, String notes, Instant createdAt,
                    List<LineView> lines, DealPricing.Totals totals, List<String> warnings,
                    ChangeOrderService.Contract contract) {
    }

    record DealListItem(UUID id, String number, String title, DealStatus status, Direction direction,
                        String currency, PartyView customer, DealPricing.Totals totals, Instant createdAt) {
    }

    record ReverseRequest(@NotNull @DecimalMin("0") BigDecimal unitPrice, @NotNull BigDecimal markupPct) {
    }

    private final DealService service;
    private final ChangeOrderService changeOrders;
    private final CounterpartyLookup counterparties;
    private final BigDecimal marginWarningPct;

    DealController(DealService service, ChangeOrderService changeOrders, CounterpartyLookup counterparties,
                   @Value("${dealos.deals.margin-warning-pct:}") BigDecimal marginWarningPct) {
        this.service = service;
        this.changeOrders = changeOrders;
        this.counterparties = counterparties;
        this.marginWarningPct = marginWarningPct;
    }

    @GetMapping
    List<DealListItem> search(@RequestParam(required = false) String q,
                              @RequestParam(required = false) DealStatus status,
                              @RequestParam(required = false) UUID customerId) {
        Map<UUID, CounterpartySummary> cache = new HashMap<>();
        return service.search(q, status, customerId).stream()
                .map(d -> new DealListItem(d.getId(), d.getNumber(), d.getTitle(), d.getStatus(), d.getDirection(),
                        d.getCurrency(), PartyView.of(party(d.getCustomerId(), cache)), d.totals(), d.getCreatedAt()))
                .toList();
    }

    @GetMapping("/{id}")
    DealView get(@PathVariable UUID id) {
        return view(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DealView create(@Valid @RequestBody CreateRequest r) {
        return view(service.create(new DealService.NewDeal(r.title(), r.customerId(), r.direction(), r.siteCountry(),
                r.currency().toUpperCase(), r.defaultMarkupPct())));
    }

    @PutMapping("/{id}")
    DealView edit(@PathVariable UUID id, @Valid @RequestBody EditRequest r) {
        return view(service.edit(id, r.title(), r.defaultMarkupPct(), r.siteCountry(), r.notes()));
    }

    @PostMapping("/{id}/lines")
    DealView addLine(@PathVariable UUID id, @Valid @RequestBody LineRequest r) {
        return view(service.addLine(id, r.data()));
    }

    @PutMapping("/{id}/lines/{lineId}")
    DealView updateLine(@PathVariable UUID id, @PathVariable UUID lineId, @Valid @RequestBody LineRequest r) {
        return view(service.updateLine(id, lineId, r.data()));
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    DealView removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        return view(service.removeLine(id, lineId));
    }

    @PostMapping("/{id}/status")
    DealView moveTo(@PathVariable UUID id, @Valid @RequestBody StatusRequest r) {
        return view(service.moveTo(id, r.status()));
    }

    /** Helper for negotiations: highest unit cost that keeps the markup within the customer's price. */
    @PostMapping("/pricing/max-cost")
    Map<String, BigDecimal> maxCost(@Valid @RequestBody ReverseRequest r) {
        return Map.of("maxUnitCost", DealPricing.maxUnitCost(r.unitPrice(), r.markupPct()));
    }

    private DealView view(Deal d) {
        Map<UUID, CounterpartySummary> cache = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        CounterpartySummary customer = party(d.getCustomerId(), cache);
        if (customer != null && customer.isRed()) {
            warnings.add("CUSTOMER_RED");
        }
        List<LineView> lines = new ArrayList<>();
        for (DealLine l : d.getLines()) {
            DealPricing.PricedLine p = DealPricing.price(l.pricingInput(), d.getDefaultMarkupPct());
            CounterpartySummary sub = l.getSubcontractorId() == null ? null : party(l.getSubcontractorId(), cache);
            if (sub != null && sub.isRed() && !warnings.contains("SUBCONTRACTOR_RED")) {
                warnings.add("SUBCONTRACTOR_RED");
            }
            if (p.margin().signum() < 0 && !warnings.contains("LINE_BELOW_COST")) {
                warnings.add("LINE_BELOW_COST");
            }
            lines.add(new LineView(l.getId(), l.getPosition(), l.getKind(), l.getDescription(), l.getWorkType(),
                    l.getUnit(), l.getQuantity(), l.getUnitCost(), p.markupPct(),
                    l.getMarkupPct() == null && l.getFixedUnitPrice() == null, l.getFixedUnitPrice(), p.unitPrice(),
                    p.costTotal(), p.priceTotal(), p.margin(), PartyView.of(sub)));
        }
        DealPricing.Totals totals = d.totals();
        ChangeOrderService.Contract contract = changeOrders.contract(d);
        DealPricing.Totals agreed = contract.total();
        if (marginWarningPct != null && agreed.marginPct() != null && agreed.marginPct().compareTo(marginWarningPct) < 0) {
            warnings.add("MARGIN_BELOW_THRESHOLD");
        }
        return new DealView(d.getId(), d.getNumber(), d.getTitle(), d.getStatus(), d.getStatus().next(),
                d.getDirection(), d.getSiteCountry(), d.getCurrency(), d.getDefaultMarkupPct(),
                d.getStatus().allowsPriceChanges(), PartyView.of(customer), d.getManager(), d.getNotes(),
                d.getCreatedAt(), lines, totals, warnings, contract);
    }

    private CounterpartySummary party(UUID id, Map<UUID, CounterpartySummary> cache) {
        return cache.computeIfAbsent(id, k -> counterparties.find(k).orElse(null));
    }
}
