package com.aleruan.dealos.subcontracting.api;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.subcontracting.application.RfqService;
import com.aleruan.dealos.subcontracting.domain.Rfq;
import com.aleruan.dealos.subcontracting.domain.RfqInvitation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a subcontractor sees through the private link: the items to price, nothing about the customer,
 * Aleruan's selling prices or other offers.
 */
@RestController
@RequestMapping("/api/public/rfq")
class PublicOfferController {

    record Item(UUID id, String description, String unit, BigDecimal quantity) {
    }

    record OfferPage(String title, String invitedCompany, String currency, LocalDate deadline, boolean open,
                     List<Item> items, Map<UUID, BigDecimal> myPrices, String myNote, Instant submittedAt) {
    }

    /** Offers come from outside the company: sizes and amounts are bounded. */
    record OfferRequest(@NotNull @jakarta.validation.constraints.Size(max = 500)
                        Map<@NotNull UUID, @NotNull @jakarta.validation.constraints.DecimalMin("0")
                                @jakarta.validation.constraints.Digits(integer = 12, fraction = 2) BigDecimal> prices,
                        @jakarta.validation.constraints.Size(max = 2000) String note) {
    }

    private final RfqService service;
    private final CounterpartyLookup counterparties;
    private final DealCatalog deals;

    PublicOfferController(RfqService service, CounterpartyLookup counterparties, DealCatalog deals) {
        this.service = service;
        this.counterparties = counterparties;
        this.deals = deals;
    }

    @GetMapping("/{token}")
    OfferPage open(@PathVariable String token) {
        return page(service.openByToken(token));
    }

    @PostMapping("/{token}/offer")
    OfferPage submit(@PathVariable String token, @Valid @RequestBody OfferRequest r) {
        return page(service.submitOffer(token, r.prices(), r.note()));
    }

    private OfferPage page(RfqInvitation inv) {
        Rfq rfq = inv.getRfq();
        String company = counterparties.find(inv.getSubcontractorId()).map(c -> c.legalName()).orElse("");
        String currency = deals.find(rfq.getDealId()).map(DealCatalog.DealSummary::currency).orElse(null);
        return new OfferPage(rfq.getTitle(), company, currency, rfq.getDeadline(), service.acceptsOffers(rfq),
                rfq.getItems().stream().map(i -> new Item(i.getId(), i.getDescription(), i.getUnit(), i.getQuantity())).toList(),
                inv.getOfferPrices(), inv.getOfferNote(), inv.getOfferSubmittedAt());
    }
}
