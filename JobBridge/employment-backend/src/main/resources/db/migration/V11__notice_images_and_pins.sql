ALTER TABLE notices ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE notice_images (
    notice_id BIGINT PRIMARY KEY,
    image_data MEDIUMBLOB NOT NULL,
    FOREIGN KEY (notice_id) REFERENCES notices(notice_id) ON DELETE CASCADE
);
