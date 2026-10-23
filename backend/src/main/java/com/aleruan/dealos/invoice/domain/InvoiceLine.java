package com.aleruan.dealos.invoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_line")
public class InvoiceLine {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String description;

    private String unit;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "stage_id")
    private UUID stageId;

    protected InvoiceLine() {
    }

    InvoiceLine(Invoice invoice, int position, String description, String unit, BigDecimal quantity,
                BigDecimal unitPrice, UUID stageId) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Every line needs a description");
        }
        if (quantity == null || quantity.signum() == 0) {
            throw new IllegalArgumentException("Quantity cannot be zero");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("Every line needs a unit price");
        }
        this.id = UUID.randomUUID();
        this.invoice = invoice;
        this.position = position;
        this.description = description.strip();
        this.unit = unit == null || unit.isBlank() ? null : unit.strip();
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.total = InvoiceMath.lineTotal(quantity, unitPrice);
        this.stageId = stageId;
    }

    public UUID getId() { return id; }
    public int getPosition() { return position; }
    public String getDescription() { return description; }
    public String getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotal() { return total; }
    public UUID getStageId() { return stageId; }
}
