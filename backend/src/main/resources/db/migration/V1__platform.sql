-- Platform: immutable audit log and reference data.

CREATE TABLE audit_event (
    seq          BIGINT PRIMARY KEY,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    actor        VARCHAR(200),
    entity_type  VARCHAR(100),
    entity_id    VARCHAR(100),
    action       VARCHAR(100),
    payload      TEXT,          -- stored verbatim: the hash covers these exact bytes
    prev_hash    VARCHAR(64)  NOT NULL,
    hash         VARCHAR(64)  NOT NULL UNIQUE
);

CREATE INDEX audit_event_entity_idx ON audit_event (entity_type, entity_id);

-- Append-only: no UPDATE, DELETE or TRUNCATE, whoever connects.
CREATE FUNCTION audit_event_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only (% refused)', TG_OP;
END;
$$;

CREATE TRIGGER audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_append_only();

CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_append_only();

CREATE TABLE work_type (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(40)  NOT NULL UNIQUE,
    name_cs             VARCHAR(200) NOT NULL,
    name_pl             VARCHAR(200) NOT NULL,
    name_en             VARCHAR(200) NOT NULL,
    name_ru             VARCHAR(200) NOT NULL,
    default_markup_pct  NUMERIC(6, 2),   -- set by the admin; no default invented here
    active              BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Starting catalogue: the work types listed in Aleruan's construction contract (§1).
INSERT INTO work_type (id, code, name_cs, name_pl, name_en, name_ru) VALUES
    (gen_random_uuid(), 'CONSTRUCTION', 'Stavební práce',          'Roboty budowlane',     'Construction works', 'Строительные работы'),
    (gen_random_uuid(), 'DISMANTLING',  'Demontážní práce',        'Prace demontażowe',    'Dismantling works',  'Демонтажные работы'),
    (gen_random_uuid(), 'INSTALLATION', 'Montážní práce',          'Prace instalacyjne',   'Installation works', 'Монтажные работы'),
    (gen_random_uuid(), 'FINISHING',    'Dokončovací práce',       'Prace wykończeniowe',  'Finishing works',    'Отделочные работы'),
    (gen_random_uuid(), 'INTERIOR',     'Vnitřní práce',           'Prace wewnętrzne',     'Interior works',     'Внутренние работы'),
    (gen_random_uuid(), 'ELECTRICAL',   'Elektroinstalační práce', 'Roboty elektryczne',   'Electrical works',   'Электромонтажные работы');
