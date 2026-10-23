package com.aleruan.dealos.subcontracting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** A deal line put out to tender (copied, so later edits of the deal don't change what was asked). */
@Entity
@Table(name = "rfq_item")
public class RfqItem {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "rfq_id")
    private Rfq rfq;

    private int position;

    @Column(name = "deal_line_id", nullable = false)
    private UUID dealLineId;

    @Column(nullable = false)
    private String description;

    private String unit;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "work_type")
    private String workType;

    protected RfqItem() {
    }

    RfqItem(Rfq rfq, int position, UUID dealLineId, String description, String unit, BigDecimal quantity, String workType) {
        this.id = UUID.randomUUID();
        this.rfq = rfq;
        this.position = position;
        this.dealLineId = dealLineId;
        this.description = description;
        this.unit = unit;
        this.quantity = quantity;
        this.workType = workType;
    }

    public UUID getId() { return id; }
    public int getPosition() { return position; }
    public UUID getDealLineId() { return dealLineId; }
    public String getDescription() { return description; }
    public String getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public String getWorkType() { return workType; }

    @Override
    public boolean equals(Object o) {
        return o instanceof RfqItem other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
