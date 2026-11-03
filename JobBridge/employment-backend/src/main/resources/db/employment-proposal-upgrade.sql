CREATE TABLE IF NOT EXISTS employment_interview_proposals (
    proposal_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    referral_id BIGINT NOT NULL,
    interview_at TIMESTAMP NOT NULL,
    interview_type VARCHAR(20) NOT NULL,
    location VARCHAR(500),
    meeting_url VARCHAR(500),
    applicant_guidance TEXT,
    proposed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_response VARCHAR(30),
    user_requests VARCHAR(2000),
    responded_at TIMESTAMP,
    FOREIGN KEY (referral_id) REFERENCES employment_referrals(referral_id),
    CONSTRAINT chk_proposal_type CHECK (interview_type IN ('ONLINE','OFFLINE')),
    CONSTRAINT chk_proposal_response CHECK (user_response IS NULL OR user_response IN ('ACCEPTED','CHANGE_REQUESTED','DECLINED'))
);
