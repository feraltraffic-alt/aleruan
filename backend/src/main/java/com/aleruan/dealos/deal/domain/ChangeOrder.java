package com.aleruan.dealos.deal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Extra or changed work on a won deal, priced like deal lines and approved by the customer separately. */
@Entity
@Table(name = "change_order")
public class ChangeOrder {

    @Id
    private UUID id;

    @Column(name = "deal_id", nullable = false, updatable = false)
    private UUID dealId;

    @Column(nullable = false, updatable = false)
    private int seq;

    @Column(nullable = false)
    private String title;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChangeOrderStatus status = ChangeOrderStatus.DRAFT;

    /** How many calendar days the change adds to (or, negative, removes from) the schedule. */
    @Column(name = "extra_days", nullable = false)
    private int extraDays;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Version
    private long version;

    @OneToMany(mappedBy = "changeOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position")
    private List<ChangeOrderLine> lines = new ArrayList<>();

    protected ChangeOrder() {
    }

    public ChangeOrder(UUID dealId, int seq, String title, String reason, int extraDays, Instant createdAt, String createdBy) {
        this.id = UUID.randomUUID();
        this.dealId = dealId;
        this.seq = seq;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        edit(title, reason, extraDays);
    }

    public void edit(String title, String reason, int extraDays) {
        requirePrices();
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A change order needs a title");
        }
        this.title = title.strip();
        this.reason = reason == null || reason.isBlank() ? null : reason.strip();
        this.extraDays = extraDays;
    }

    public ChangeOrderLine addLine(LineKind kind, String description, String workType, String unit, BigDecimal quantity,
                                   BigDecimal unitCost, BigDecimal markupPct, BigDecimal fixedUnitPrice, UUID subcontractorId) {
        requirePrices();
        int position = lines.stream().mapToInt(ChangeOrderLine::getPosition).max().orElse(0) + 1;
        ChangeOrderLine line = new ChangeOrderLine(this, position, kind, description, workType, unit, quantity, unitCost,
                markupPct, fixedUnitPrice, subcontractorId);
        lines.add(line);
        return line;
    }

    public void updateLine(UUID lineId, LineKind kind, String description, String workType, String unit,
                           BigDecimal quantity, BigDecimal unitCost, BigDecimal markupPct, BigDecimal fixedUnitPrice,
                           UUID subcontractorId, BigDecimal dealMarkupPct) {
        ChangeOrderLine line = line(lineId);
        if (status.allowsPriceChanges()) {
            line.update(kind, description, workType, unit, quantity, unitCost, markupPct, fixedUnitPrice, subcontractorId);
            return;
        }
        if (!status.allowsCostChanges()) {
            throw new IllegalStateException("Change order " + seq + " is " + status + " and cannot change");
        }
        boolean priceTouched = line.getKind() != kind || !line.getDescription().equals(description == null ? null : description.trim())
                || line.getQuantity().compareTo(quantity) != 0 || !same(line.getMarkupPct(), markupPct)
                || !same(line.getFixedUnitPrice(), fixedUnitPrice);
        if (priceTouched) {
            throw new IllegalStateException("The customer was already sent change order " + seq + "; revise it to change prices");
        }
        // the price the customer saw stays; only cost and subcontractor move
        line.pinUnitPrice(DealPricing.price(line.pricingInput(), dealMarkupPct).unitPrice());
        line.assignCost(unitCost, subcontractorId);
    }

    public void removeLine(UUID lineId) {
        requirePrices();
        lines.remove(line(lineId));
    }

    public void send(Instant at) {
        if (lines.isEmpty()) {
            throw new IllegalStateException("A change order needs at least one line");
        }
        move(ChangeOrderStatus.SENT);
        this.sentAt = at;
    }

    public void approve(String by, String note, Instant at) {
        decide(ChangeOrderStatus.APPROVED, by, note, at);
    }

    public void reject(String by, String note, Instant at) {
        decide(ChangeOrderStatus.REJECTED, by, note, at);
    }

    public void revise() {
        move(ChangeOrderStatus.DRAFT);
        this.sentAt = null;
    }

    public void cancel() {
        move(ChangeOrderStatus.CANCELLED);
    }

    private void decide(ChangeOrderStatus target, String by, String note, Instant at) {
        if (by == null || by.isBlank()) {
            throw new IllegalArgumentException("Record who decided on the customer's side");
        }
        move(target);
        this.decidedBy = by.strip();
        this.decisionNote = note == null || note.isBlank() ? null : note.strip();
        this.decidedAt = at;
    }

    private void move(ChangeOrderStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Change order " + seq + " cannot move from " + status + " to " + target);
        }
        this.status = target;
    }

    /** Lines without their own markup use the deal's default markup. */
    public DealPricing.Totals totals(BigDecimal dealMarkupPct) {
        return DealPricing.total(lines.stream().map(l -> DealPricing.price(l.pricingInput(), dealMarkupPct)).toList());
    }

    public ChangeOrderLine line(UUID lineId) {
        return lines.stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Line " + lineId + " is not part of change order " + seq));
    }

    private void requirePrices() {
        if (!status.allowsPriceChanges()) {
            throw new IllegalStateException("Change order " + seq + " is " + status + "; revise it to a draft first");
        }
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    public String number(String dealNumber) {
        return dealNumber + "-C" + seq;
    }

    public UUID getId() { return id; }
    public UUID getDealId() { return dealId; }
    public int getSeq() { return seq; }
    public String getTitle() { return title; }
    public String getReason() { return reason; }
    public ChangeOrderStatus getStatus() { return status; }
    public int getExtraDays() { return extraDays; }
    public Instant getSentAt() { return sentAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecidedBy() { return decidedBy; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public List<ChangeOrderLine> getLines() { return Collections.unmodifiableList(lines); }
}
