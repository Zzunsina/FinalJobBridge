CREATE TABLE IF NOT EXISTS home_promotions (
    promotion_key VARCHAR(32) PRIMARY KEY,
    content_json MEDIUMTEXT NOT NULL,
    sort_order INT NOT NULL,
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 0,
    image_data MEDIUMBLOB,
    image_type VARCHAR(30),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS home_promotion_deletions (
    promotion_key VARCHAR(32) PRIMARY KEY,
    deleted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
