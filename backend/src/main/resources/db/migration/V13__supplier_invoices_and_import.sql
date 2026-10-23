-- Invoices received from subcontractors and suppliers, and documents waiting to be read by Claude.

CREATE TABLE supplier_invoice (
    id               UUID PRIMARY KEY,
    supplier_id      UUID           NOT NULL,
    deal_id          UUID           REFERENCES deal (id),
    number           VARCHAR(60)    NOT NULL,
    variable_symbol  VARCHAR(20),
    issue_date       DATE,
    taxable_date     DATE,
    due_date         DATE,
    currency         VARCHAR(3)     NOT NULL,
    net_amount       NUMERIC(16, 2),
    vat_amount       NUMERIC(16, 2),
    total            NUMERIC(16, 2) NOT NULL,
    vat_note         TEXT,
    bank_account     VARCHAR(60),
    account_check    VARCHAR(10),
    file_id          UUID,
    file_sha256      CHAR(64),
    import_id        UUID,
    paid_on          DATE,
    note             TEXT,
    created_at       TIMESTAMPTZ    NOT NULL,
    created_by       VARCHAR(200),
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT supplier_invoice_number_unique UNIQUE (supplier_id, number)
);
CREATE INDEX supplier_invoice_deal_idx ON supplier_invoice (deal_id);

CREATE TABLE import_document (
    id              UUID PRIMARY KEY,
    file_id         UUID          NOT NULL,
    file_name       VARCHAR(255)  NOT NULL,
    content_type    VARCHAR(100)  NOT NULL,
    sha256          CHAR(64)      NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    doc_type        VARCHAR(30),
    extracted       TEXT,
    warnings        TEXT,
    error           TEXT,
    model           VARCHAR(60),
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    attempts        INTEGER       NOT NULL DEFAULT 0,
    deal_id         UUID,
    result_type     VARCHAR(30),
    result_id       UUID,
    uploaded_at     TIMESTAMPTZ   NOT NULL,
    uploaded_by     VARCHAR(200),
    processed_at    TIMESTAMPTZ,
    decided_at      TIMESTAMPTZ,
    decided_by      VARCHAR(200),
    version         BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX import_document_status_idx ON import_document (status, uploaded_at);
CREATE INDEX import_document_sha_idx ON import_document (sha256);
