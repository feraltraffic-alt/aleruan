package com.aleruan.dealos.subcontracting.application;

import com.aleruan.dealos.counterparty.CounterpartyLookup;
import com.aleruan.dealos.counterparty.CounterpartySummary;
import com.aleruan.dealos.deal.DealCatalog;
import com.aleruan.dealos.platform.audit.AuditService;
import com.aleruan.dealos.platform.security.CurrentActor;
import com.aleruan.dealos.platform.web.NotFoundException;
import com.aleruan.dealos.subcontracting.domain.InviteTokens;
import com.aleruan.dealos.subcontracting.domain.Rfq;
import com.aleruan.dealos.subcontracting.domain.RfqInvitation;
import com.aleruan.dealos.subcontracting.domain.RfqItem;
import com.aleruan.dealos.subcontracting.domain.RfqRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RfqService {

    public static final String AUDIT_TYPE = "Rfq";
    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");

    /** Invitation result: the private link token is returned exactly once. */
    public record NewInvitation(RfqInvitation invitation, String token) {
    }

    private final RfqRepository rfqs;
    private final DealCatalog deals;
    private final CounterpartyLookup counterparties;
    private final AuditService audit;
    private final CurrentActor actor;
    private final Clock clock;

    RfqService(RfqRepository rfqs, DealCatalog deals, CounterpartyLookup counterparties, AuditService audit,
               CurrentActor actor, Clock clock) {
        this.rfqs = rfqs;
        this.deals = deals;
        this.counterparties = counterparties;
        this.audit = audit;
        this.actor = actor;
        this.clock = clock;
    }

    @Transactional
    public Rfq create(UUID dealId, String title, LocalDate deadline, Set<UUID> lineIds) {
        DealCatalog.DealSummary deal = deals.find(dealId)
                .orElseThrow(() -> new NotFoundException("Deal " + dealId + " not found"));
        if (Set.of("LOST", "COMPLETED", "CANCELLED").contains(deal.status())) {
            throw new IllegalStateException("Deal " + deal.number() + " is closed");
        }
        if (lineIds == null || lineIds.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one deal line");
        }
        if (deadline != null && deadline.isBefore(today())) {
            throw new IllegalArgumentException("The deadline is in the past");
        }
        List<DealCatalog.LineSummary> lines = deals.lines(dealId).stream().filter(l -> lineIds.contains(l.id())).toList();
        if (lines.size() != lineIds.size()) {
            throw new IllegalArgumentException("Some lines are not part of deal " + deal.number());
        }
        Rfq rfq = new Rfq(dealId, title == null || title.isBlank() ? deal.title() : title.trim(), deadline,
                actor.name(), clock.instant());
        lines.forEach(l -> rfq.addItem(l.id(), l.description(), l.unit(), l.quantity(), l.workType()));
        rfqs.save(rfq);
        audit.record(AUDIT_TYPE, rfq.getId().toString(), "CREATED", Map.of("dealId", dealId, "lines", lineIds,
                "deadline", String.valueOf(deadline)));
        return rfq;
    }

    @Transactional
    public NewInvitation invite(UUID rfqId, UUID subcontractorId, boolean allowRisky) {
        Rfq rfq = get(rfqId);
        CounterpartySummary sub = counterparties.find(subcontractorId)
                .orElseThrow(() -> new IllegalArgumentException("Counterparty " + subcontractorId + " does not exist"));
        if (!sub.hasRole("SUBCONTRACTOR") && !sub.hasRole("SUPPLIER")) {
            throw new IllegalArgumentException(sub.legalName() + " is not a subcontractor or supplier");
        }
        if (sub.isRed() && !allowRisky) {
            throw new IllegalStateException(sub.legalName() + " failed the registry check (red). Confirm to invite anyway.");
        }
        String token = InviteTokens.newToken();
        RfqInvitation inv = rfq.invite(subcontractorId, InviteTokens.hash(token), clock.instant());
        audit.record(AUDIT_TYPE, rfqId.toString(), "INVITED", Map.of("subcontractorId", subcontractorId,
                "invitationId", inv.getId(), "riskyConfirmed", sub.isRed()));
        return new NewInvitation(inv, token);
    }

    @Transactional
    public Rfq award(UUID rfqId, UUID invitationId) {
        Rfq rfq = get(rfqId);
        RfqInvitation winner = rfq.award(invitationId);
        for (RfqItem item : rfq.getItems()) {
            BigDecimal price = winner.getOfferPrices().get(item.getId());
            deals.assignCost(rfq.getDealId(), item.getDealLineId(), price, winner.getSubcontractorId(),
                    "RFQ " + rfq.getId() + " awarded");
        }
        audit.record(AUDIT_TYPE, rfqId.toString(), "AWARDED", Map.of("invitationId", invitationId,
                "subcontractorId", winner.getSubcontractorId(), "prices", stringKeys(winner.getOfferPrices())));
        return rfq;
    }

    @Transactional
    public Rfq cancel(UUID rfqId) {
        Rfq rfq = get(rfqId);
        rfq.cancel();
        audit.record(AUDIT_TYPE, rfqId.toString(), "CANCELLED", null);
        return rfq;
    }

    @Transactional(readOnly = true)
    public Rfq get(UUID id) {
        return rfqs.findById(id).orElseThrow(() -> new NotFoundException("Request " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Rfq> forDeal(UUID dealId) {
        return rfqs.findByDealIdOrderByCreatedAtDesc(dealId);
    }

    // ---- private link (subcontractor side) ----

    /** Opens the offer page; records the first view. Unknown token → not found (no hint whether it ever existed). */
    @Transactional
    public RfqInvitation openByToken(String token) {
        Rfq rfq = byToken(token);
        RfqInvitation inv = invitationByToken(rfq, token);
        if (inv.getViewedAt() == null) {
            audit.recordAs(subActor(inv), AUDIT_TYPE, rfq.getId().toString(), "VIEWED", Map.of("invitationId", inv.getId()));
        }
        inv.markViewed(clock.instant());
        return inv;
    }

    @Transactional
    public RfqInvitation submitOffer(String token, Map<UUID, BigDecimal> prices, String note) {
        Rfq rfq = byToken(token);
        RfqInvitation inv = invitationByToken(rfq, token);
        rfq.submitOffer(inv, prices, note == null ? null : note.trim(), today(), clock.instant());
        audit.recordAs(subActor(inv), AUDIT_TYPE, rfq.getId().toString(), "OFFER_SUBMITTED", Map.of(
                "invitationId", inv.getId(), "prices", stringKeys(prices), "note", String.valueOf(note)));
        return inv;
    }

    private Rfq byToken(String token) {
        return rfqs.findByTokenHash(InviteTokens.hash(token))
                .orElseThrow(() -> new NotFoundException("This link is not valid"));
    }

    private static RfqInvitation invitationByToken(Rfq rfq, String token) {
        String hash = InviteTokens.hash(token);
        return rfq.getInvitations().stream()
                .filter(i -> i.hasTokenHash(hash)).findFirst()
                .orElseThrow(() -> new NotFoundException("This link is not valid"));
    }

    private static String subActor(RfqInvitation inv) {
        return "subcontractor:" + inv.getSubcontractorId();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(PRAGUE));
    }

    private static Map<String, BigDecimal> stringKeys(Map<UUID, BigDecimal> m) {
        return m.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
    }
}
