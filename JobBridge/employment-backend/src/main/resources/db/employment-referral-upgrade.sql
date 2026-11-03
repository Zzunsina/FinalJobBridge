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
