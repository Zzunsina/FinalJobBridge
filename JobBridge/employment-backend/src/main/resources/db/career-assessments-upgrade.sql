CREATE TABLE IF NOT EXISTS career_assessment_attempts (
 attempt_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 version VARCHAR(30) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
 answers_json MEDIUMTEXT NOT NULL,
 report_json MEDIUMTEXT,
 revision INT NOT NULL DEFAULT 0,
 started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deadline_at TIMESTAMP NOT NULL,
 completed_at TIMESTAMP NULL,
 FOREIGN KEY (user_id) REFERENCES users(user_id),
 INDEX idx_career_attempt_owner (user_id, attempt_id)
);
