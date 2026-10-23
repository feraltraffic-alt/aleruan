package com.aleruan.dealos.subcontracting.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A tender for some lines of one deal. */
@Entity
@Table(name = "rfq")
public class Rfq {

    @Id
    private UUID id;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Column(nullable = false)
    private String title;

    /** Offers are accepted until the end of this day (Europe/Prague). */
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RfqStatus status = RfqStatus.OPEN;

    @Column(name = "awarded_invitation_id")
    private UUID awardedInvitationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    // Sets (not lists) so both can be fetched eagerly; order is restored in the getters.
    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    private java.util.Set<RfqItem> items = new java.util.LinkedHashSet<>();

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    private java.util.Set<RfqInvitation> invitations = new java.util.LinkedHashSet<>();

    @Version
    private long version;

    protected Rfq() {
    }

    public Rfq(UUID dealId, String title, LocalDate deadline, String createdBy, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.dealId = dealId;
        this.title = title;
        this.deadline = deadline;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void addItem(UUID dealLineId, String description, String unit, BigDecimal quantity, String workType) {
        items.add(new RfqItem(this, items.size() + 1, dealLineId, description, unit, quantity, workType));
    }

    public RfqInvitation invite(UUID subcontractorId, String tokenHash, Instant at) {
        requireOpen();
        if (invitations.stream().anyMatch(i -> i.getSubcontractorId().equals(subcontractorId))) {
            throw new IllegalStateException("This subcontractor is already invited");
        }
        RfqInvitation inv = new RfqInvitation(this, subcontractorId, tokenHash, at);
        invitations.add(inv);
        return inv;
    }

    public void submitOffer(RfqInvitation invitation, Map<UUID, BigDecimal> prices, String note, LocalDate today, Instant at) {
        requireOpen();
        if (deadline != null && today.isAfter(deadline)) {
            throw new IllegalStateException("The deadline for offers has passed");
        }
        List<OfferComparison.Item> wanted = comparisonItems();
        if (!OfferComparison.pricesAll(wanted, prices) || prices.size() != wanted.size()) {
            throw new IllegalArgumentException("Please price every item of the request");
        }
        invitation.submitOffer(prices, note, at);
    }

    public RfqInvitation award(UUID invitationId) {
        requireOpen();
        RfqInvitation winner = invitation(invitationId);
        if (!winner.hasOffer()) {
            throw new IllegalStateException("This subcontractor has not submitted an offer");
        }
        this.status = RfqStatus.AWARDED;
        this.awardedInvitationId = invitationId;
        return winner;
    }

    public void cancel() {
        requireOpen();
        this.status = RfqStatus.CANCELLED;
    }

    public OfferComparison.Result compare() {
        return OfferComparison.compare(comparisonItems(), invitations.stream().filter(RfqInvitation::hasOffer)
                .map(i -> new OfferComparison.Offer(i.getId(), i.getOfferPrices())).toList());
    }

    public RfqInvitation invitation(UUID invitationId) {
        return invitations.stream().filter(i -> i.getId().equals(invitationId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invitation " + invitationId + " is not part of this request"));
    }

    private List<OfferComparison.Item> comparisonItems() {
        return items.stream().map(i -> new OfferComparison.Item(i.getId(), i.getQuantity())).toList();
    }

    private void requireOpen() {
        if (status != RfqStatus.OPEN) {
            throw new IllegalStateException("The request is " + status.name().toLowerCase());
        }
    }

    public UUID getId() { return id; }
    public UUID getDealId() { return dealId; }
    public String getTitle() { return title; }
    public LocalDate getDeadline() { return deadline; }
    public RfqStatus getStatus() { return status; }
    public UUID getAwardedInvitationId() { return awardedInvitationId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public List<RfqItem> getItems() {
        return items.stream().sorted(java.util.Comparator.comparingInt(RfqItem::getPosition)).toList();
    }

    public List<RfqInvitation> getInvitations() {
        return invitations.stream().sorted(java.util.Comparator.comparing(RfqInvitation::getInvitedAt)
                .thenComparing(i -> i.getId().toString())).toList();
    }
}
