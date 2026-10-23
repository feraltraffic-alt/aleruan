-- Files (photos, scans) stored with the record they belong to; site diary; defects and warranty.

CREATE TABLE stored_file (
    id            UUID PRIMARY KEY,
    owner_type    VARCHAR(40)   NOT NULL,
    owner_id      UUID          NOT NULL,
    file_name     VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    size_bytes    INTEGER       NOT NULL,
    sha256        CHAR(64)      NOT NULL,
    data          BYTEA         NOT NULL,
    uploaded_at   TIMESTAMPTZ   NOT NULL,
    uploaded_by   VARCHAR(200)
);

CREATE INDEX stored_file_owner_idx ON stored_file (owner_type, owner_id, uploaded_at);

-- Stavební deník: one or more entries per day, written by the foreman or PM.
CREATE TABLE diary_entry (
    id           UUID PRIMARY KEY,
    site_id      UUID          NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    entry_date   DATE          NOT NULL,
    weather      VARCHAR(200),
    workers      INTEGER       CHECK (workers IS NULL OR workers >= 0),
    text         TEXT          NOT NULL,
    corrects_id  UUID          REFERENCES diary_entry (id),
    author       VARCHAR(200),
    created_at   TIMESTAMPTZ   NOT NULL,
    edited_at    TIMESTAMPTZ,
    version      BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX diary_entry_site_idx ON diary_entry (site_id, entry_date DESC, created_at DESC);

CREATE TABLE defect (
    id                UUID PRIMARY KEY,
    site_id           UUID          NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    seq               INTEGER       NOT NULL,
    title             VARCHAR(300)  NOT NULL,
    description       TEXT,
    location          VARCHAR(300),
    severity          VARCHAR(20)   NOT NULL,
    subcontractor_id  UUID,
    stage_id          UUID          REFERENCES acceptance_stage (id) ON DELETE SET NULL,
    found_on          DATE          NOT NULL,
    due_date          DATE,
    warranty          BOOLEAN       NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)   NOT NULL,
    fixed_at          TIMESTAMPTZ,
    fix_note          TEXT,
    closed_at         TIMESTAMPTZ,
    closed_by         VARCHAR(200),
    created_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(200),
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT defect_seq_unique UNIQUE (site_id, seq)
);

CREATE INDEX defect_status_idx ON defect (status, due_date);
CREATE INDEX defect_sub_idx ON defect (subcontractor_id);

ALTER TABLE site ADD COLUMN handover_date DATE;
ALTER TABLE site ADD COLUMN warranty_months INTEGER CHECK (warranty_months IS NULL OR warranty_months BETWEEN 0 AND 120);
