-- Tenders (RFQ) for deal lines, invitations by private link, offers.

CREATE TABLE rfq (
    id                     UUID PRIMARY KEY,
    deal_id                UUID         NOT NULL REFERENCES deal (id),
    title                  VARCHAR(300) NOT NULL,
    deadline               DATE,
    status                 VARCHAR(20)  NOT NULL,
    awarded_invitation_id  UUID,
    created_at             TIMESTAMPTZ  NOT NULL,
    created_by             VARCHAR(200),
    version                BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX rfq_deal_idx ON rfq (deal_id, created_at DESC);

CREATE TABLE rfq_item (
    id            UUID PRIMARY KEY,
    rfq_id        UUID           NOT NULL REFERENCES rfq (id) ON DELETE CASCADE,
    position      INTEGER        NOT NULL,
    deal_line_id  UUID           NOT NULL,
    description   VARCHAR(1000)  NOT NULL,
    unit          VARCHAR(20),
    quantity      NUMERIC(14, 4) NOT NULL,
    work_type     VARCHAR(40)
);

CREATE TABLE rfq_invitation (
    id                  UUID PRIMARY KEY,
    rfq_id              UUID        NOT NULL REFERENCES rfq (id) ON DELETE CASCADE,
    subcontractor_id    UUID        NOT NULL REFERENCES counterparty (id),
    token_hash          VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 of the private link token; the token itself is never stored
    invited_at          TIMESTAMPTZ NOT NULL,
    viewed_at           TIMESTAMPTZ,
    offer_submitted_at  TIMESTAMPTZ,
    offer_note          TEXT,
    CONSTRAINT rfq_invitation_once UNIQUE (rfq_id, subcontractor_id)
);

CREATE TABLE rfq_offer_line (
    invitation_id  UUID           NOT NULL REFERENCES rfq_invitation (id) ON DELETE CASCADE,
    item_id        UUID           NOT NULL REFERENCES rfq_item (id) ON DELETE CASCADE,
    unit_price     NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (invitation_id, item_id)
);
