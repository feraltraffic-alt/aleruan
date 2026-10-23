-- Counterparties and evidence of registry checks.

CREATE TABLE counterparty (
    id               UUID PRIMARY KEY,
    legal_name       VARCHAR(300) NOT NULL,
    country          CHAR(2)      NOT NULL,
    company_id       VARCHAR(40),            -- IČO / NIP / foreign registration number
    vat_number       VARCHAR(20),
    address          VARCHAR(500),
    email            VARCHAR(200),
    phone            VARCHAR(50),
    check_status     VARCHAR(10),            -- GREEN / YELLOW / RED, null = never checked
    last_checked_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT counterparty_company_unique UNIQUE (country, company_id)
);

CREATE INDEX counterparty_name_idx ON counterparty (lower(legal_name));

CREATE TABLE counterparty_role (
    counterparty_id  UUID        NOT NULL REFERENCES counterparty (id) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL,
    PRIMARY KEY (counterparty_id, role)
);

CREATE TABLE check_result (
    id                  UUID PRIMARY KEY,
    counterparty_id     UUID         NOT NULL REFERENCES counterparty (id),
    source              VARCHAR(20)  NOT NULL,
    status              VARCHAR(10)  NOT NULL,
    findings            VARCHAR(500),
    request_id          VARCHAR(200),   -- e.g. VIES consultation number: proof of the check
    unavailable_reason  VARCHAR(500),
    raw_response        TEXT,
    checked_at          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX check_result_counterparty_idx ON check_result (counterparty_id, checked_at DESC);
