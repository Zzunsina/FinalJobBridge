CREATE TABLE IF NOT EXISTS employment_interview_coordination (
    referral_id BIGINT PRIMARY KEY,
    company_available_times VARCHAR(2000),
    user_response VARCHAR(20),
    user_available_times VARCHAR(2000),
    user_requests VARCHAR(2000),
    user_responded_at TIMESTAMP,
    FOREIGN KEY (referral_id) REFERENCES employment_referrals(referral_id),
    CONSTRAINT chk_coordination_response CHECK (user_response IS NULL OR user_response IN ('ACCEPTED','DECLINED'))
);
