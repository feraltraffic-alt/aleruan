package com.aleruan.dealos.subcontracting.api;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.subcontracting.application.RfqService;
import com.aleruan.dealos.subcontracting.domain.OfferComparison;
import com.aleruan.dealos.subcontracting.domain.Rfq;
import com.aleruan.dealos.subcontracting.domain.RfqInvitation;
import com.aleruan.dealos.subcontracting.domain.RfqStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class RfqController {

    record CreateRequest(String title, LocalDate deadline, @NotEmpty Set<UUID> lineIds) {
    }

    record InviteRequest(@NotNull UUID subcontractorId, boolean allowRisky) {
    }

    record AwardRequest(@NotNull UUID invitationId) {
    }

    record PartyView(UUID id, String legalName, String country, String checkStatus, Set<String> workTypes) {
        static PartyView of(CounterpartySummary c) {
            return new PartyView(c.id(), c.legalName(), c.country(), c.checkStatus(), c.workTypes());
        }
    }

    record ItemView(UUID id, UUID dealLineId, String description, String unit, BigDecimal quantity, String workType,
                    UUID cheapestInvitationId) {
    }

    record InvitationView(UUID id, PartyView subcontractor, Instant invitedAt, Instant viewedAt,
                          Instant offerSubmittedAt, String offerNote, Map<UUID, BigDecimal> prices,
                          BigDecimal total, boolean complete) {
    }

    record RfqView(UUID id, UUID dealId, String dealNumber, String currency, String title, LocalDate deadline,
                   RfqStatus status, UUID awardedInvitationId, UUID cheapestInvitationId, List<ItemView> items,
                   List<InvitationView> invitations) {
    }

    /** The private link path; the frontend prefixes its own origin. */
    record InvitationCreated(InvitationView invitation, String offerPath) {
    }

    private final RfqService service;
    private final CounterpartyLookup counterparties;
    private final DealCatalog deals;

    RfqController(RfqService service, CounterpartyLookup counterparties, DealCatalog deals) {
        this.service = service;
        this.counterparties = counterparties;
        this.deals = deals;
    }

    @PostMapping("/deals/{dealId}/rfqs")
    @ResponseStatus(HttpStatus.CREATED)
    RfqView create(@PathVariable UUID dealId, @Valid @RequestBody CreateRequest r) {
        return view(service.create(dealId, r.title(), r.deadline(), r.lineIds()));
    }

    @GetMapping("/deals/{dealId}/rfqs")
    List<RfqView> forDeal(@PathVariable UUID dealId) {
        return service.forDeal(dealId).stream().map(this::view).toList();
    }

    @GetMapping("/rfqs/{id}")
    RfqView get(@PathVariable UUID id) {
        return view(service.get(id));
    }

    @PostMapping("/rfqs/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationCreated invite(@PathVariable UUID id, @Valid @RequestBody InviteRequest r) {
        RfqService.NewInvitation created = service.invite(id, r.subcontractorId(), r.allowRisky());
        Rfq rfq = service.get(id);
        return new InvitationCreated(invitationView(created.invitation(), rfq.compare()), "/offer/" + created.token());
    }

    @PostMapping("/rfqs/{id}/award")
    RfqView award(@PathVariable UUID id, @Valid @RequestBody AwardRequest r) {
        return view(service.award(id, r.invitationId()));
    }

    @PostMapping("/rfqs/{id}/cancel")
    RfqView cancel(@PathVariable UUID id) {
        return view(service.cancel(id));
    }

    /** Subcontractors and suppliers who can be invited, optionally filtered by work type and country. */
    @GetMapping("/rfqs/candidates")
    List<PartyView> candidates(@RequestParam(required = false) String workType,
                               @RequestParam(required = false) String country) {
        return Stream.concat(counterparties.withRole("SUBCONTRACTOR", workType, country).stream(),
                        counterparties.withRole("SUPPLIER", workType, country).stream())
                .collect(Collectors.toMap(CounterpartySummary::id, PartyView::of, (a, b) -> a, java.util.LinkedHashMap::new))
                .values().stream().toList();
    }

    private RfqView view(Rfq rfq) {
        OfferComparison.Result cmp = rfq.compare();
        DealCatalog.DealSummary deal = deals.find(rfq.getDealId()).orElse(null);
        List<ItemView> items = rfq.getItems().stream().map(i -> new ItemView(i.getId(), i.getDealLineId(),
                i.getDescription(), i.getUnit(), i.getQuantity(), i.getWorkType(),
                cmp.cheapestOfferPerItem().get(i.getId()))).toList();
        List<InvitationView> invitations = new ArrayList<>();
        rfq.getInvitations().forEach(inv -> invitations.add(invitationView(inv, cmp)));
        return new RfqView(rfq.getId(), rfq.getDealId(), deal == null ? null : deal.number(),
                deal == null ? null : deal.currency(), rfq.getTitle(), rfq.getDeadline(), rfq.getStatus(),
                rfq.getAwardedInvitationId(), cmp.cheapestCompleteOffer(), items, invitations);
    }

    private InvitationView invitationView(RfqInvitation inv, OfferComparison.Result cmp) {
        CounterpartySummary sub = counterparties.find(inv.getSubcontractorId()).orElseThrow();
        OfferComparison.OfferTotal total = cmp.totals().stream().filter(t -> t.offerId().equals(inv.getId()))
                .findFirst().orElse(null);
        return new InvitationView(inv.getId(), PartyView.of(sub), inv.getInvitedAt(), inv.getViewedAt(),
                inv.getOfferSubmittedAt(), inv.getOfferNote(), inv.getOfferPrices(),
                total == null ? null : total.total(), total != null && total.complete());
    }
}
