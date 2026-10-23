-- Staged acceptance (поэтапная приёмка / předávání po etapách): parts of a site handed over to the
-- customer, each with its share of the contract value; accepted stages are the basis for invoicing.

CREATE TABLE acceptance_stage (
    id            UUID PRIMARY KEY,
    site_id       UUID           NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    seq           INTEGER        NOT NULL,
    title         VARCHAR(300)   NOT NULL,
    description   TEXT,
    amount        NUMERIC(16, 2) NOT NULL CHECK (amount >= 0),
    planned_date  DATE,
    status        VARCHAR(30)    NOT NULL,
    submitted_at  TIMESTAMPTZ,
    decided_at    TIMESTAMPTZ,
    decided_by    VARCHAR(200),
    remarks       TEXT,
    fix_until     DATE,
    created_at    TIMESTAMPTZ    NOT NULL,
    created_by    VARCHAR(200),
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT acceptance_stage_seq_unique UNIQUE (site_id, seq)
);

CREATE TABLE acceptance_stage_task (
    stage_id  UUID NOT NULL REFERENCES acceptance_stage (id) ON DELETE CASCADE,
    task_id   UUID NOT NULL,
    PRIMARY KEY (stage_id, task_id)
);
