-- Change orders (доп. работы / změnové listy): extra or changed work agreed with the customer after
-- the original prices were frozen. Approved ones add to the contract value.

CREATE TABLE change_order (
    id             UUID PRIMARY KEY,
    deal_id        UUID          NOT NULL REFERENCES deal (id),
    seq            INTEGER       NOT NULL,
    title          VARCHAR(300)  NOT NULL,
    reason         TEXT,
    status         VARCHAR(20)   NOT NULL,
    extra_days     INTEGER       NOT NULL DEFAULT 0,
    sent_at        TIMESTAMPTZ,
    decided_at     TIMESTAMPTZ,
    decided_by     VARCHAR(200),   -- the customer's person who approved or rejected
    decision_note  TEXT,
    created_at     TIMESTAMPTZ   NOT NULL,
    created_by     VARCHAR(200),
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT change_order_seq_unique UNIQUE (deal_id, seq)
);

CREATE TABLE change_order_line (
    id                UUID PRIMARY KEY,
    change_order_id   UUID           NOT NULL REFERENCES change_order (id) ON DELETE CASCADE,
    position          INTEGER        NOT NULL,
    kind              VARCHAR(20)    NOT NULL,
    description       VARCHAR(1000)  NOT NULL,
    work_type         VARCHAR(40),
    unit              VARCHAR(20),
    quantity          NUMERIC(14, 4) NOT NULL,
    unit_cost         NUMERIC(14, 2) NOT NULL,
    markup_pct        NUMERIC(7, 2),
    fixed_unit_price  NUMERIC(14, 2),
    subcontractor_id  UUID
);

CREATE INDEX change_order_deal_idx ON change_order (deal_id, seq);
