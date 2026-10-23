package com.aleruan.dealos.subcontracting.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One subcontractor invited to a tender, with their current offer (a resubmission replaces it). */
@Entity
@Table(name = "rfq_invitation")
public class RfqInvitation {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "rfq_id")
    private Rfq rfq;

    @Column(name = "subcontractor_id", nullable = false)
    private UUID subcontractorId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "offer_submitted_at")
    private Instant offerSubmittedAt;

    @Column(name = "offer_note", columnDefinition = "text")
    private String offerNote;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rfq_offer_line", joinColumns = @JoinColumn(name = "invitation_id"))
    @MapKeyColumn(name = "item_id")
    @Column(name = "unit_price")
    private Map<UUID, BigDecimal> offerPrices = new HashMap<>();

    protected RfqInvitation() {
    }

    RfqInvitation(Rfq rfq, UUID subcontractorId, String tokenHash, Instant invitedAt) {
        this.id = UUID.randomUUID();
        this.rfq = rfq;
        this.subcontractorId = subcontractorId;
        this.tokenHash = tokenHash;
        this.invitedAt = invitedAt;
    }

    public void markViewed(Instant at) {
        if (viewedAt == null) {
            viewedAt = at;
        }
    }

    void submitOffer(Map<UUID, BigDecimal> prices, String note, Instant at) {
        this.offerPrices = new HashMap<>(prices);
        this.offerNote = note;
        this.offerSubmittedAt = at;
    }

    public boolean hasTokenHash(String hash) {
        return tokenHash.equals(hash);
    }

    public boolean hasOffer() {
        return offerSubmittedAt != null;
    }

    public UUID getId() { return id; }
    public Rfq getRfq() { return rfq; }
    public UUID getSubcontractorId() { return subcontractorId; }
    public Instant getInvitedAt() { return invitedAt; }
    public Instant getViewedAt() { return viewedAt; }
    public Instant getOfferSubmittedAt() { return offerSubmittedAt; }
    public String getOfferNote() { return offerNote; }
    public Map<UUID, BigDecimal> getOfferPrices() { return Map.copyOf(offerPrices); }

    @Override
    public boolean equals(Object o) {
        return o instanceof RfqInvitation other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
