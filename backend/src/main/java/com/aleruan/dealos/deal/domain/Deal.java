package com.aleruan.dealos.deal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Aggregate root: a deal and its lines. All rules about what may change when live here. */
@Entity
@Table(name = "deal")
public class Deal {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String title;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    /** Country where the work is performed (often different from the customer's country). */
    @Column(name = "site_country", length = 2)
    private String siteCountry;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "default_markup_pct", nullable = false)
    private BigDecimal defaultMarkupPct;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DealStatus status = DealStatus.DRAFT;

    private String manager;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Eager: a deal is always shown with its lines (dozens at most), also outside the service transaction.
    @OneToMany(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    @OrderBy("position")
    private List<DealLine> lines = new ArrayList<>();

    @Version
    private long version;

    protected Deal() {
    }

    public Deal(String number, String title, UUID customerId, Direction direction, String siteCountry, String currency,
                BigDecimal defaultMarkupPct, String manager, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.number = number;
        this.title = title;
        this.customerId = customerId;
        this.direction = direction;
        this.siteCountry = siteCountry;
        this.currency = java.util.Currency.getInstance(currency).getCurrencyCode();
        this.defaultMarkupPct = requireMarkup(defaultMarkupPct);
        this.manager = manager;
        this.createdAt = createdAt;
    }

    public void edit(String title, BigDecimal defaultMarkupPct, String siteCountry, String notes) {
        if (defaultMarkupPct != null && defaultMarkupPct.compareTo(this.defaultMarkupPct) != 0) {
            requirePriceChangesAllowed();
            this.defaultMarkupPct = requireMarkup(defaultMarkupPct);
        }
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        this.siteCountry = siteCountry;
        this.notes = notes;
    }

    public DealLine addLine(LineKind kind, String description, String workType, String unit, BigDecimal quantity,
                            BigDecimal unitCost, BigDecimal markupPct, BigDecimal fixedUnitPrice, UUID subcontractorId) {
        requirePriceChangesAllowed();
        int position = lines.stream().mapToInt(DealLine::getPosition).max().orElse(0) + 1;
        DealLine line = new DealLine(this, position, kind, description, workType, unit, quantity, unitCost,
                markupPct, fixedUnitPrice, subcontractorId);
        lines.add(line);
        return line;
    }

    public DealLine updateLine(UUID lineId, LineKind kind, String description, String workType, String unit,
                               BigDecimal quantity, BigDecimal unitCost, BigDecimal markupPct,
                               BigDecimal fixedUnitPrice, UUID subcontractorId) {
        DealLine line = line(lineId);
        boolean priceTouched = line.getQuantity().compareTo(quantity) != 0
                || !same(line.getMarkupPct(), markupPct) || !same(line.getFixedUnitPrice(), fixedUnitPrice)
                || line.getKind() != kind || !line.getDescription().equals(description);
        if (priceTouched) {
            requirePriceChangesAllowed();
        } else {
            requireCostChangesAllowed();
        }
        line.update(kind, description, workType, unit, quantity, unitCost, markupPct, fixedUnitPrice, subcontractorId);
        return line;
    }

    public void removeLine(UUID lineId) {
        requirePriceChangesAllowed();
        lines.remove(line(lineId));
    }

    /** Result of a tender: the winning subcontractor and their unit cost. Selling price is not touched. */
    public void assignCost(UUID lineId, BigDecimal unitCost, UUID subcontractorId) {
        requireCostChangesAllowed();
        line(lineId).assignCost(unitCost, subcontractorId);
    }

    public void moveTo(DealStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Deal " + number + " cannot move from " + status + " to " + target);
        }
        if (target == DealStatus.QUOTED && lines.isEmpty()) {
            throw new IllegalStateException("A quote needs at least one line");
        }
        this.status = target;
    }

    public DealPricing.Totals totals() {
        return DealPricing.total(lines.stream().map(l -> DealPricing.price(l.pricingInput(), defaultMarkupPct)).toList());
    }

    public DealLine line(UUID lineId) {
        return lines.stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Line " + lineId + " is not part of deal " + number));
    }

    private void requirePriceChangesAllowed() {
        if (!status.allowsPriceChanges()) {
            throw new IllegalStateException("Prices of deal " + number + " are frozen in status " + status
                    + "; use a change order");
        }
    }

    private void requireCostChangesAllowed() {
        if (!status.allowsCostChanges()) {
            throw new IllegalStateException("Deal " + number + " is closed (" + status + ")");
        }
    }

    private static BigDecimal requireMarkup(BigDecimal markup) {
        if (markup == null || markup.compareTo(BigDecimal.valueOf(-100)) <= 0) {
            throw new IllegalArgumentException("Markup must be greater than -100 %");
        }
        return markup;
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    public UUID getId() { return id; }
    public String getNumber() { return number; }
    public String getTitle() { return title; }
    public UUID getCustomerId() { return customerId; }
    public Direction getDirection() { return direction; }
    public String getSiteCountry() { return siteCountry; }
    public String getCurrency() { return currency; }
    public BigDecimal getDefaultMarkupPct() { return defaultMarkupPct; }
    public DealStatus getStatus() { return status; }
    public String getManager() { return manager; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public List<DealLine> getLines() { return List.copyOf(lines); }
}
