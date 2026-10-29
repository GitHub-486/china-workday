CREATE TABLE IF NOT EXISTS holiday (
    id           BIGSERIAL PRIMARY KEY,
    holiday_date DATE        NOT NULL UNIQUE,
    holiday_name VARCHAR(64),
    day_type     VARCHAR(16) NOT NULL,
    year         INT         NOT NULL,
    source       VARCHAR(32),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_holiday_year  ON holiday (year);
CREATE INDEX IF NOT EXISTS idx_holiday_date  ON holiday (holiday_date);

CREATE TABLE IF NOT EXISTS crawl_url (
    id           BIGSERIAL PRIMARY KEY,
    url          VARCHAR(512) NOT NULL UNIQUE,
    year         INT,
    fetched_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
