-- Existing databases: back up and manually apply only these new business tables.

CREATE TABLE IF NOT EXISTS resume_files (
    file_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    file_name VARCHAR(200) NOT NULL, file_data MEDIUMBLOB NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (resume_id) REFERENCES resumes(resume_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS resume_assessments (
    resume_id BIGINT PRIMARY KEY,
    score INT NOT NULL CHECK(score BETWEEN 0 AND 100), version VARCHAR(30) NOT NULL,
    FOREIGN KEY (resume_id) REFERENCES resumes(resume_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS job_assessment_settings (
    job_id BIGINT PRIMARY KEY, min_score INT NOT NULL CHECK(min_score BETWEEN 0 AND 100),
    FOREIGN KEY (job_id) REFERENCES job_postings(job_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS application_assessments (
    application_id BIGINT PRIMARY KEY,
    score INT NOT NULL CHECK(score BETWEEN 0 AND 100), min_score INT,
    first_viewed_at TIMESTAMP NULL, review_due_at TIMESTAMP NULL,
    review_notified BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (application_id) REFERENCES applications(application_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS employment_company_reviews (
    employment_link_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    review_status VARCHAR(30) NOT NULL,
    opinion TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (employment_link_id, company_id),
    FOREIGN KEY (employment_link_id) REFERENCES employment_links(employment_link_id),
    FOREIGN KEY (company_id) REFERENCES companies(company_id),
    CONSTRAINT chk_employment_review_status CHECK (review_status IN ('REVIEWING','INTERVIEW_REQUESTED','HIRE_REQUESTED','DECLINED'))
);
CREATE TABLE IF NOT EXISTS employment_referrals (
    referral_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employment_link_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    job_id BIGINT,
    resume_snapshot MEDIUMTEXT NOT NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    opinion TEXT,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    interview_at TIMESTAMP,
    interview_location VARCHAR(500),
    FOREIGN KEY (employment_link_id) REFERENCES employment_links(employment_link_id),
    FOREIGN KEY (company_id) REFERENCES companies(company_id),
    FOREIGN KEY (job_id) REFERENCES job_postings(job_id),
    CONSTRAINT chk_referral_review CHECK (review_status IN ('PENDING','INTERVIEW_REQUESTED','HIRE_REQUESTED','DECLINED','CANCELED'))
);

CREATE TABLE IF NOT EXISTS employment_preferences (
    employment_link_id BIGINT PRIMARY KEY,
    preferred_industry VARCHAR(100) NOT NULL,
    preferred_occupation VARCHAR(100) NOT NULL,
    FOREIGN KEY (employment_link_id) REFERENCES employment_links(employment_link_id)
);
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
