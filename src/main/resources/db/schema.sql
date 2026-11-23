-- china-workday schema
-- holiday_date 唯一，用于幂等 upsert
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

-- 抓取 URL 去重表（避免重复抓取同一详情页；year 为信息列，便于按年份统计）
CREATE TABLE IF NOT EXISTS crawl_url (
    id           BIGSERIAL PRIMARY KEY,
    url          VARCHAR(512) NOT NULL UNIQUE,
    year         INT,
    fetched_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
