-- Bank-account checks and the EU consolidated sanctions list.

ALTER TABLE check_result ADD COLUMN checked_account VARCHAR(60);
ALTER TABLE check_result ALTER COLUMN request_id TYPE VARCHAR(500);

CREATE TABLE sanction_import (
    id           BIGSERIAL PRIMARY KEY,
    imported_at  TIMESTAMPTZ NOT NULL,
    entities     INTEGER     NOT NULL,
    aliases      INTEGER     NOT NULL,
    file_sha256  VARCHAR(64) NOT NULL
);

CREATE TABLE sanction_alias (
    id               BIGSERIAL PRIMARY KEY,
    logical_id       VARCHAR(40),
    subject_type     VARCHAR(20),
    whole_name       VARCHAR(1000) NOT NULL,
    normalized_name  VARCHAR(1000) NOT NULL
);

CREATE INDEX sanction_alias_normalized_idx ON sanction_alias (normalized_name);
