CREATE TABLE IF NOT EXISTS holiday (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    holiday_date DATE        NOT NULL UNIQUE,
    holiday_name VARCHAR(64),
    day_type     VARCHAR(16) NOT NULL,
    year         INT         NOT NULL,
    source       VARCHAR(32),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_holiday_year (year),
    INDEX idx_holiday_date (holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crawl_url (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    url          VARCHAR(512) NOT NULL UNIQUE,
    year         INT,
    fetched_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
