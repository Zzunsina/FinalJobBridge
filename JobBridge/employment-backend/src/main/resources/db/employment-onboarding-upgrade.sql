CREATE TABLE IF NOT EXISTS employment_onboarding (
    referral_id BIGINT PRIMARY KEY,
    start_at TIMESTAMP,
    first_day_location VARCHAR(500),
    preparations TEXT,
    contact VARCHAR(300),
    instructions TEXT,
    updated_at TIMESTAMP,
    acknowledged_at TIMESTAMP,
    joined_at TIMESTAMP,
    not_joined_at TIMESTAMP,
    user_message VARCHAR(2000),
    FOREIGN KEY (referral_id) REFERENCES employment_referrals(referral_id)
);
-- Previous automatic completion meant company hiring intent, not confirmed attendance.
UPDATE employment_links SET status='INTERVIEW',completed_at=NULL
WHERE status='EMPLOYED' AND EXISTS (
    SELECT 1 FROM employment_referrals r
    WHERE r.employment_link_id=employment_links.employment_link_id AND r.review_status='HIRE_REQUESTED'
    AND r.referral_id=(SELECT MAX(r2.referral_id) FROM employment_referrals r2 WHERE r2.employment_link_id=employment_links.employment_link_id)
) AND NOT EXISTS (
    SELECT 1 FROM employment_onboarding o JOIN employment_referrals r ON r.referral_id=o.referral_id
    WHERE r.employment_link_id=employment_links.employment_link_id AND o.joined_at IS NOT NULL
);
