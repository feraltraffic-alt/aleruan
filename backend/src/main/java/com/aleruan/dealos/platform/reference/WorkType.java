package com.aleruan.dealos.platform.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Editable catalogue of work types (estimates, filters, default markup). */
@Entity
@Table(name = "work_type")
public class WorkType {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "name_cs", nullable = false)
    private String nameCs;

    @Column(name = "name_pl", nullable = false)
    private String namePl;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_ru", nullable = false)
    private String nameRu;

    @Column(name = "default_markup_pct")
    private BigDecimal defaultMarkupPct;

    private boolean active = true;

    protected WorkType() {
    }

    public WorkType(String code, String nameCs, String namePl, String nameEn, String nameRu,
                    BigDecimal defaultMarkupPct) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.nameCs = nameCs;
        this.namePl = namePl;
        this.nameEn = nameEn;
        this.nameRu = nameRu;
        this.defaultMarkupPct = defaultMarkupPct;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getNameCs() { return nameCs; }
    public String getNamePl() { return namePl; }
    public String getNameEn() { return nameEn; }
    public String getNameRu() { return nameRu; }
    public BigDecimal getDefaultMarkupPct() { return defaultMarkupPct; }
    public boolean isActive() { return active; }
}
