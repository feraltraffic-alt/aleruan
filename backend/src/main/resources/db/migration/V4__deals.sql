-- Work types of subcontractors/suppliers, deals and deal lines.

CREATE TABLE counterparty_work_type (
    counterparty_id  UUID        NOT NULL REFERENCES counterparty (id) ON DELETE CASCADE,
    work_type        VARCHAR(40) NOT NULL,
    PRIMARY KEY (counterparty_id, work_type)
);

CREATE SEQUENCE deal_number_seq START 1;

CREATE TABLE deal (
    id                  UUID PRIMARY KEY,
    number              VARCHAR(20)   NOT NULL UNIQUE,
    title               VARCHAR(300)  NOT NULL,
    customer_id         UUID          NOT NULL REFERENCES counterparty (id),
    direction           VARCHAR(20)   NOT NULL,
    site_country        CHAR(2),
    currency            CHAR(3)       NOT NULL,
    default_markup_pct  NUMERIC(7, 2) NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    manager             VARCHAR(200),
    notes               TEXT,
    created_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX deal_customer_idx ON deal (customer_id);
CREATE INDEX deal_status_idx ON deal (status, created_at DESC);

CREATE TABLE deal_line (
    id                UUID PRIMARY KEY,
    deal_id           UUID           NOT NULL REFERENCES deal (id) ON DELETE CASCADE,
    position          INTEGER        NOT NULL,
    kind              VARCHAR(20)    NOT NULL,
    description       VARCHAR(1000)  NOT NULL,
    work_type         VARCHAR(40),
    unit              VARCHAR(20),
    quantity          NUMERIC(14, 4) NOT NULL CHECK (quantity > 0),
    unit_cost         NUMERIC(14, 2) NOT NULL CHECK (unit_cost >= 0),
    markup_pct        NUMERIC(7, 2),
    fixed_unit_price  NUMERIC(14, 2) CHECK (fixed_unit_price >= 0),
    subcontractor_id  UUID REFERENCES counterparty (id)
);

CREATE INDEX deal_line_deal_idx ON deal_line (deal_id, position);
CREATE INDEX deal_line_subcontractor_idx ON deal_line (subcontractor_id);
