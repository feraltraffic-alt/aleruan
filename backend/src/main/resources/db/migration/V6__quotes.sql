-- Quotes (cenová nabídka / КП): one row per issued version, never changed afterwards.

CREATE TABLE quote (
    id           UUID PRIMARY KEY,
    deal_id      UUID           NOT NULL REFERENCES deal (id),
    number       VARCHAR(40)    NOT NULL,
    version      INTEGER        NOT NULL,
    language     VARCHAR(2)     NOT NULL,
    vat_mode     VARCHAR(30)    NOT NULL,
    vat_rate     NUMERIC(5, 2)  NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    subtotal     NUMERIC(16, 2) NOT NULL,
    vat_amount   NUMERIC(16, 2) NOT NULL,
    total        NUMERIC(16, 2) NOT NULL,
    issued_on    DATE           NOT NULL,
    valid_until  DATE           NOT NULL,
    issued_at    TIMESTAMPTZ    NOT NULL,
    issued_by    VARCHAR(200),
    document     TEXT           NOT NULL,  -- QuoteDocument as JSON: exactly what was printed
    pdf          BYTEA          NOT NULL,
    pdf_sha256   CHAR(64)       NOT NULL,
    CONSTRAINT quote_version_unique UNIQUE (deal_id, version)
);

CREATE INDEX quote_deal_idx ON quote (deal_id, version DESC);

CREATE FUNCTION quote_immutable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'issued quotes cannot be changed (% refused); issue a new version', TG_OP;
END;
$$;

CREATE TRIGGER quote_no_update_delete
    BEFORE UPDATE OR DELETE ON quote
    FOR EACH ROW EXECUTE FUNCTION quote_immutable();
