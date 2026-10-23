-- Issued invoices (faktura – daňový doklad) and credit notes (opravný daňový doklad).
-- Numbers come from a gap-free series per year and type, as Czech tax rules expect.

CREATE TABLE invoice_series (
    id     VARCHAR(40) PRIMARY KEY,   -- e.g. INVOICE-2026
    last   INTEGER     NOT NULL
);

CREATE TABLE invoice (
    id               UUID PRIMARY KEY,
    deal_id          UUID           NOT NULL REFERENCES deal (id),
    customer_id      UUID           NOT NULL,
    kind             VARCHAR(20)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    number           VARCHAR(30)    UNIQUE,
    variable_symbol  VARCHAR(10),
    credit_of_id     UUID           REFERENCES invoice (id),
    language         VARCHAR(2)     NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    vat_mode         VARCHAR(30)    NOT NULL,
    vat_rate         NUMERIC(5, 2)  NOT NULL,
    issue_date       DATE,
    taxable_date     DATE           NOT NULL,
    due_date         DATE           NOT NULL,
    subtotal         NUMERIC(16, 2) NOT NULL,
    vat_amount       NUMERIC(16, 2) NOT NULL,
    total            NUMERIC(16, 2) NOT NULL,
    exchange_rate    NUMERIC(12, 6),
    rate_date        DATE,
    vat_amount_czk   NUMERIC(16, 2),
    note             TEXT,
    paid_amount      NUMERIC(16, 2) NOT NULL DEFAULT 0,
    paid_on          DATE,
    document         TEXT,
    pdf              BYTEA,
    pdf_sha256       CHAR(64),
    created_at       TIMESTAMPTZ    NOT NULL,
    created_by       VARCHAR(200),
    issued_at        TIMESTAMPTZ,
    issued_by        VARCHAR(200),
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT invoice_issued_complete CHECK (status = 'DRAFT' OR (number IS NOT NULL AND pdf IS NOT NULL AND issue_date IS NOT NULL))
);

CREATE INDEX invoice_deal_idx ON invoice (deal_id);
CREATE INDEX invoice_status_due_idx ON invoice (status, due_date);

CREATE TABLE invoice_line (
    id                UUID PRIMARY KEY,
    invoice_id        UUID           NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    position          INTEGER        NOT NULL,
    description       VARCHAR(1000)  NOT NULL,
    unit              VARCHAR(20),
    quantity          NUMERIC(14, 4) NOT NULL,
    unit_price        NUMERIC(16, 4) NOT NULL,
    total             NUMERIC(16, 2) NOT NULL,
    stage_id          UUID
);

CREATE INDEX invoice_line_stage_idx ON invoice_line (stage_id) WHERE stage_id IS NOT NULL;

-- Once issued, the document is evidence: content cannot change and the row cannot be deleted.
-- Only payment, status (paid / credited) and bookkeeping columns move afterwards.
CREATE FUNCTION invoice_issued_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'issued invoice % cannot be deleted; issue a credit note', OLD.number;
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status <> 'DRAFT' AND (
        NEW.number IS DISTINCT FROM OLD.number OR NEW.total IS DISTINCT FROM OLD.total
        OR NEW.subtotal IS DISTINCT FROM OLD.subtotal OR NEW.vat_amount IS DISTINCT FROM OLD.vat_amount
        OR NEW.pdf_sha256 IS DISTINCT FROM OLD.pdf_sha256 OR NEW.document IS DISTINCT FROM OLD.document
        OR NEW.taxable_date IS DISTINCT FROM OLD.taxable_date OR NEW.issue_date IS DISTINCT FROM OLD.issue_date
        OR NEW.customer_id IS DISTINCT FROM OLD.customer_id OR NEW.status = 'DRAFT') THEN
        RAISE EXCEPTION 'issued invoice % cannot be changed; issue a credit note', OLD.number;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER invoice_issued_guard
    BEFORE UPDATE OR DELETE ON invoice
    FOR EACH ROW EXECUTE FUNCTION invoice_issued_guard();

CREATE FUNCTION invoice_line_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    s VARCHAR(20);
BEGIN
    SELECT status INTO s FROM invoice WHERE id = COALESCE(NEW.invoice_id, OLD.invoice_id);
    IF s IS NOT NULL AND s <> 'DRAFT' THEN
        RAISE EXCEPTION 'lines of an issued invoice cannot be changed';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER invoice_line_guard
    BEFORE INSERT OR UPDATE OR DELETE ON invoice_line
    FOR EACH ROW EXECUTE FUNCTION invoice_line_guard();
