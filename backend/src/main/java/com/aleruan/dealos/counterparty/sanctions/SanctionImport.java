package com.aleruan.dealos.counterparty.sanctions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One successful import of the EU list: when, how many names, and the file hash (evidence of the version used). */
@Entity
@Table(name = "sanction_import")
public class SanctionImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "entities", nullable = false)
    private int entities;

    @Column(name = "aliases", nullable = false)
    private int aliases;

    @Column(name = "file_sha256", nullable = false)
    private String fileSha256;

    protected SanctionImport() {
    }

    SanctionImport(Instant importedAt, int entities, int aliases, String fileSha256) {
        this.importedAt = importedAt;
        this.entities = entities;
        this.aliases = aliases;
        this.fileSha256 = fileSha256;
    }

    public Instant getImportedAt() { return importedAt; }
    public int getEntities() { return entities; }
    public int getAliases() { return aliases; }
    public String getFileSha256() { return fileSha256; }
}
