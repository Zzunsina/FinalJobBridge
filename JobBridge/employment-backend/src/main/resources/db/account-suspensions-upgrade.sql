CREATE TABLE IF NOT EXISTS account_suspensions (
    user_id BIGINT PRIMARY KEY,
    reason VARCHAR(1000) NOT NULL,
    suspended_by BIGINT NOT NULL,
    suspended_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lifted_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (suspended_by) REFERENCES users(user_id)
);
