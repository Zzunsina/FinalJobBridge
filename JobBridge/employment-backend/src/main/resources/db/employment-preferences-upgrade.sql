CREATE TABLE IF NOT EXISTS employment_preferences (
    employment_link_id BIGINT PRIMARY KEY,
    preferred_industry VARCHAR(100) NOT NULL,
    preferred_occupation VARCHAR(100) NOT NULL,
    FOREIGN KEY (employment_link_id) REFERENCES employment_links(employment_link_id)
);
UPDATE employment_links SET status='MATCHING' WHERE status='COUNSELING';
