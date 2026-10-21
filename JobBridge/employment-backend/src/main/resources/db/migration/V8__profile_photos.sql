CREATE TABLE user_profile_photos (
    user_id BIGINT PRIMARY KEY,
    image_data MEDIUMBLOB NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
