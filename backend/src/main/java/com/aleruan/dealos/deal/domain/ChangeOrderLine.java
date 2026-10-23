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

@Entity
@Table(name = "change_order_line")
public class ChangeOrderLine {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "change_order_id")
    private ChangeOrder changeOrder;

    @Column(nullable = false)
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

    @Column(name = "markup_pct")
    private BigDecimal markupPct;

    @Column(name = "fixed_unit_price")
    private BigDecimal fixedUnitPrice;

    @Column(name = "subcontractor_id")
    private UUID subcontractorId;

    protected ChangeOrderLine() {
    }

    ChangeOrderLine(ChangeOrder changeOrder, int position, LineKind kind, String description, String workType,
                    String unit, BigDecimal quantity, BigDecimal unitCost, BigDecimal markupPct,
                    BigDecimal fixedUnitPrice, UUID subcontractorId) {
        this.id = UUID.randomUUID();
        this.changeOrder = changeOrder;
        this.position = position;
        update(kind, description, workType, unit, quantity, unitCost, markupPct, fixedUnitPrice, subcontractorId);
    }

    void update(LineKind kind, String description, String workType, String unit, BigDecimal quantity,
                BigDecimal unitCost, BigDecimal markupPct, BigDecimal fixedUnitPrice, UUID subcontractorId) {
        if (kind == null || description == null || description.isBlank()) {
            throw new IllegalArgumentException("A line needs a kind and a description");
        }
        // a change order may also reduce the scope, so quantities can be negative (work taken out)
        if (quantity == null || quantity.signum() == 0) {
            throw new IllegalArgumentException("Quantity must not be zero");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost must be zero or positive");
        }
        if (fixedUnitPrice != null && fixedUnitPrice.signum() < 0) {
            throw new IllegalArgumentException("Fixed unit price must be zero or positive");
        }
        this.kind = kind;
        this.description = description.trim();
        this.workType = workType == null || workType.isBlank() ? null : workType;
        this.unit = unit == null || unit.isBlank() ? null : unit.trim();
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

    void pinUnitPrice(BigDecimal unitPrice) {
        if (fixedUnitPrice == null) {
            this.fixedUnitPrice = unitPrice;
        }
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
