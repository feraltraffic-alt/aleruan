package com.aleruan.dealos.counterparty.sanctions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sanction_alias")
class SanctionAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "logical_id")
    private String logicalId;

    @Column(name = "subject_type")
    private String subjectType;

    @Column(name = "whole_name", nullable = false)
    private String wholeName;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    protected SanctionAlias() {
    }

    SanctionAlias(String logicalId, String subjectType, String wholeName, String normalizedName) {
        this.logicalId = logicalId;
        this.subjectType = subjectType;
        this.wholeName = wholeName;
        this.normalizedName = normalizedName;
    }

    String getLogicalId() { return logicalId; }
    String getSubjectType() { return subjectType; }
    String getWholeName() { return wholeName; }
}
