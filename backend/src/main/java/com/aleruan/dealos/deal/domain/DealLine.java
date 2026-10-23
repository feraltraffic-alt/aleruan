package com.aleruan.dealos.deal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One line of a deal: what is sold, how much, what it costs and who delivers it. */
@Entity
@Table(name = "deal_line")
public class DealLine {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LineKind kind;

    @Column(nullable = false)
    private String description;

    @Column(name = "work_type")
    private String workType;

    private String unit;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    /** Null = use the deal's default markup. */
    @Column(name = "markup_pct")
    private BigDecimal markupPct;

    /** Selling price agreed with the customer; when set it wins over any markup. */
    @Column(name = "fixed_unit_price")
    private BigDecimal fixedUnitPrice;

    @Column(name = "subcontractor_id")
    private UUID subcontractorId;

    protected DealLine() {
    }

    DealLine(Deal deal, int position, LineKind kind, String description, String workType, String unit,
             BigDecimal quantity, BigDecimal unitCost, BigDecimal markupPct, BigDecimal fixedUnitPrice,
             UUID subcontractorId) {
        this.id = UUID.randomUUID();
        this.deal = deal;
        this.position = position;
        update(kind, description, workType, unit, quantity, unitCost, markupPct, fixedUnitPrice, subcontractorId);
    }

    void update(LineKind kind, String description, String workType, String unit, BigDecimal quantity,
                BigDecimal unitCost, BigDecimal markupPct, BigDecimal fixedUnitPrice, UUID subcontractorId) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost must be zero or positive");
        }
        if (fixedUnitPrice != null && fixedUnitPrice.signum() < 0) {
            throw new IllegalArgumentException("Selling price must be zero or positive");
        }
        this.kind = kind;
        this.description = description;
        this.workType = workType;
        this.unit = unit;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.markupPct = markupPct;
        this.fixedUnitPrice = fixedUnitPrice;
        this.subcontractorId = subcontractorId;
    }

    void assignCost(BigDecimal unitCost, UUID subcontractorId) {
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost must be zero or positive");
        }
        this.unitCost = unitCost;
        this.subcontractorId = subcontractorId;
    }

    public DealPricing.LineInput pricingInput() {
        return new DealPricing.LineInput(quantity, unitCost, markupPct, fixedUnitPrice);
    }

    public UUID getId() { return id; }
    public int getPosition() { return position; }
    public LineKind getKind() { return kind; }
    public String getDescription() { return description; }
    public String getWorkType() { return workType; }
    public String getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getMarkupPct() { return markupPct; }
    public BigDecimal getFixedUnitPrice() { return fixedUnitPrice; }
    public UUID getSubcontractorId() { return subcontractorId; }
}
