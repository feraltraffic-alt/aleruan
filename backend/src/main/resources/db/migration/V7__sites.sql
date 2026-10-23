-- Sites (объекты): where a won deal is executed, its schedule and who works there.

CREATE TABLE site (
    id             UUID PRIMARY KEY,
    deal_id        UUID          NOT NULL UNIQUE REFERENCES deal (id),
    name           VARCHAR(300)  NOT NULL,
    address        VARCHAR(500),
    city           VARCHAR(120),
    country        VARCHAR(2),
    latitude       NUMERIC(9, 6),
    longitude      NUMERIC(9, 6),
    project_manager VARCHAR(200),
    foreman        VARCHAR(200),
    planned_start  DATE,
    planned_end    DATE,
    status         VARCHAR(20)   NOT NULL,
    notes          TEXT,
    created_at     TIMESTAMPTZ   NOT NULL,
    created_by     VARCHAR(200),
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT site_dates CHECK (planned_end IS NULL OR planned_start IS NULL OR planned_end >= planned_start),
    CONSTRAINT site_coords CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE INDEX site_country_city_idx ON site (country, city);
CREATE INDEX site_status_idx ON site (status);

CREATE TABLE site_task (
    id                UUID PRIMARY KEY,
    site_id           UUID          NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    position          INTEGER       NOT NULL,
    title             VARCHAR(500)  NOT NULL,
    work_type         VARCHAR(40),
    deal_line_id      UUID,
    subcontractor_id  UUID,
    start_date        DATE          NOT NULL,
    end_date          DATE          NOT NULL,
    progress_pct      INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT site_task_dates CHECK (end_date >= start_date),
    CONSTRAINT site_task_progress CHECK (progress_pct BETWEEN 0 AND 100)
);

CREATE INDEX site_task_site_idx ON site_task (site_id, position);
CREATE INDEX site_task_sub_dates_idx ON site_task (subcontractor_id, start_date, end_date);
