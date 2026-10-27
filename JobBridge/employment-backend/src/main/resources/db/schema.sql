-- Complete schema for a NEW, isolated database. Not an upgrade script.
-- Normal application startup does not run SQL initialization.

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(255) NOT NULL UNIQUE,
        password VARCHAR(100) NOT NULL, name VARCHAR(100) NOT NULL, phone VARCHAR(30) NOT NULL,
        role VARCHAR(20) NOT NULL CHECK (role IN ('JOB_SEEKER','COMPANY','ADMIN')),
        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','WITHDRAWN')),
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    login_id VARCHAR(30) NOT NULL, CONSTRAINT uq_users_login_id UNIQUE(login_id),
    birth_date DATE NOT NULL, gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE','FEMALE')),
    CONSTRAINT uq_users_phone UNIQUE(phone),
    CONSTRAINT chk_users_login_id_required CHECK (CHAR_LENGTH(TRIM(login_id)) > 0),
    CONSTRAINT chk_users_password_required CHECK (CHAR_LENGTH(TRIM(password)) > 0),
    CONSTRAINT chk_users_name_required CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_users_email_required CHECK (CHAR_LENGTH(TRIM(email)) > 0),
    CONSTRAINT chk_users_phone_required CHECK (CHAR_LENGTH(TRIM(phone)) > 0)
);

CREATE TABLE IF NOT EXISTS job_seekers (
    job_seeker_id BIGINT AUTO_INCREMENT PRIMARY KEY,
        user_id BIGINT NOT NULL UNIQUE, FOREIGN KEY (user_id) REFERENCES users(user_id), birth_date DATE,
        address VARCHAR(255), desired_job VARCHAR(100), desired_region VARCHAR(100),
        experience_years INT DEFAULT 0 CHECK (experience_years BETWEEN 0 AND 80), introduction TEXT
);

CREATE TABLE IF NOT EXISTS companies (
    company_id BIGINT AUTO_INCREMENT PRIMARY KEY,
        user_id BIGINT NOT NULL UNIQUE, FOREIGN KEY (user_id) REFERENCES users(user_id), company_name VARCHAR(200) NOT NULL,
        business_number VARCHAR(20) NOT NULL UNIQUE, ceo_name VARCHAR(100), industry VARCHAR(100),
        company_size VARCHAR(100), address VARCHAR(255), website VARCHAR(500), description TEXT,
        approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', review_reason TEXT,
        reviewed_by BIGINT, FOREIGN KEY (reviewed_by) REFERENCES users(user_id), reviewed_at TIMESTAMP,
    CONSTRAINT chk_company_name_required CHECK (CHAR_LENGTH(TRIM(company_name)) > 0),
    CONSTRAINT chk_business_number_required CHECK (CHAR_LENGTH(TRIM(business_number)) > 0),
    CONSTRAINT chk_company_approval CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS job_postings (
    job_id BIGINT AUTO_INCREMENT PRIMARY KEY,
        company_id BIGINT NOT NULL, FOREIGN KEY (company_id) REFERENCES companies(company_id), title VARCHAR(200) NOT NULL,
        job_type VARCHAR(50) NOT NULL, career_type VARCHAR(50) NOT NULL, education VARCHAR(100),
        salary VARCHAR(100), work_location VARCHAR(255) NOT NULL, description TEXT NOT NULL,
        requirements TEXT, preferred_qualifications TEXT, banner_image_url MEDIUMTEXT,
        headcount INT NOT NULL CHECK (headcount BETWEEN 1 AND 10000), start_date DATE NOT NULL,
        end_date DATE NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', review_reason TEXT,
        reviewed_by BIGINT, FOREIGN KEY (reviewed_by) REFERENCES users(user_id), reviewed_at TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, CHECK (start_date <= end_date),
    CONSTRAINT chk_job_status CHECK (status IN ('DRAFT', 'PENDING', 'PUBLISHED', 'CLOSED', 'REJECTED'))
);

CREATE TABLE IF NOT EXISTS resumes (
    resume_id BIGINT AUTO_INCREMENT PRIMARY KEY,
        job_seeker_id BIGINT NOT NULL, FOREIGN KEY (job_seeker_id) REFERENCES job_seekers(job_seeker_id), title VARCHAR(200) NOT NULL,
        education VARCHAR(100), career_description TEXT, skills TEXT, portfolio_url VARCHAR(500),
        is_public BOOLEAN NOT NULL DEFAULT FALSE, is_default BOOLEAN NOT NULL DEFAULT FALSE,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details_json TEXT
);

CREATE TABLE IF NOT EXISTS job_bookmarks (
    bookmark_id BIGINT AUTO_INCREMENT PRIMARY KEY,
        job_seeker_id BIGINT NOT NULL, FOREIGN KEY (job_seeker_id) REFERENCES job_seekers(job_seeker_id),
        job_id BIGINT NOT NULL, FOREIGN KEY (job_id) REFERENCES job_postings(job_id),
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(job_seeker_id,job_id)
);

CREATE TABLE IF NOT EXISTS applications (
    application_id BIGINT AUTO_INCREMENT PRIMARY KEY,
        job_id BIGINT NOT NULL, FOREIGN KEY (job_id) REFERENCES job_postings(job_id),
        job_seeker_id BIGINT NOT NULL, FOREIGN KEY (job_seeker_id) REFERENCES job_seekers(job_seeker_id),
        resume_id BIGINT NOT NULL, FOREIGN KEY (resume_id) REFERENCES resumes(resume_id), cover_letter TEXT,
        status VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_application_status CHECK (status IN ('APPLIED', 'UNDER_REVIEW', 'DOCUMENT_PASSED', 'INTERVIEW', 'FINAL_PASSED', 'REJECTED', 'CANCELED'))
);

CREATE TABLE IF NOT EXISTS interviews (
    interview_id BIGINT AUTO_INCREMENT PRIMARY KEY,
        application_id BIGINT NOT NULL, FOREIGN KEY (application_id) REFERENCES applications(application_id),
        interview_at TIMESTAMP NOT NULL, interview_type VARCHAR(20) NOT NULL,
        location VARCHAR(255), meeting_url VARCHAR(500), status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
        result TEXT, memo TEXT, request_key VARCHAR(100) NOT NULL, request_hash VARCHAR(64) NOT NULL,
        UNIQUE(application_id,request_key),
    CONSTRAINT chk_interview_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELED')),
    CONSTRAINT chk_interview_type CHECK (interview_type IN ('ONLINE', 'OFFLINE'))
);

CREATE TABLE IF NOT EXISTS employment_links (
    employment_link_id BIGINT AUTO_INCREMENT PRIMARY KEY,
        job_seeker_id BIGINT NOT NULL, FOREIGN KEY (job_seeker_id) REFERENCES job_seekers(job_seeker_id),
        company_id BIGINT, FOREIGN KEY (company_id) REFERENCES companies(company_id), job_id BIGINT, FOREIGN KEY (job_id) REFERENCES job_postings(job_id),
        manager_user_id BIGINT, FOREIGN KEY (manager_user_id) REFERENCES users(user_id), status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
        memo TEXT, public_message TEXT, requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        completed_at TIMESTAMP,
    CONSTRAINT chk_link_status CHECK (status IN ('REQUESTED', 'COUNSELING', 'MATCHING', 'INTERVIEW', 'EMPLOYED', 'CLOSED'))
);

CREATE TABLE IF NOT EXISTS notices (
    notice_id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200) NOT NULL,
        content TEXT NOT NULL, author_user_id BIGINT NOT NULL, FOREIGN KEY (author_user_id) REFERENCES users(user_id),
        is_published BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS inquiries (
    inquiry_id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, FOREIGN KEY (user_id) REFERENCES users(user_id),
        title VARCHAR(200) NOT NULL, content TEXT NOT NULL, answer TEXT, status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
        answered_by BIGINT, FOREIGN KEY (answered_by) REFERENCES users(user_id), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        answered_at TIMESTAMP,
    CONSTRAINT chk_inquiry_status CHECK (status IN ('OPEN', 'ANSWERED'))
);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
        user_id BIGINT NOT NULL, FOREIGN KEY (user_id) REFERENCES users(user_id), title VARCHAR(200) NOT NULL,
        content VARCHAR(1000) NOT NULL, notification_type VARCHAR(50), related_id BIGINT,
        is_read BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    access_hash VARCHAR(64) NOT NULL,
    refresh_hash VARCHAR(64) NOT NULL,
    access_expires_at TIMESTAMP NOT NULL,
    refresh_expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS application_snapshots (
    application_id BIGINT PRIMARY KEY,
    snapshot_json TEXT NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (application_id) REFERENCES applications(application_id)
);

CREATE TABLE IF NOT EXISTS user_profile_photos (
    user_id BIGINT PRIMARY KEY,
    image_data MEDIUMBLOB NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_signup_consents (
    user_id BIGINT PRIMARY KEY,
    terms_version VARCHAR(40) NOT NULL,
    privacy_version VARCHAR(40) NOT NULL,
    marketing_version VARCHAR(40) NOT NULL,
    terms_accepted BOOLEAN NOT NULL,
    privacy_accepted BOOLEAN NOT NULL,
    marketing_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT chk_required_signup_consents CHECK (terms_accepted=TRUE AND privacy_accepted=TRUE)
);

CREATE TABLE IF NOT EXISTS company_images (
    company_id BIGINT PRIMARY KEY,
    image_data MEDIUMBLOB NOT NULL,
    FOREIGN KEY (company_id) REFERENCES companies(company_id)
);

CREATE TABLE IF NOT EXISTS notice_images (
    notice_id BIGINT PRIMARY KEY,
    image_data MEDIUMBLOB NOT NULL,
    FOREIGN KEY (notice_id) REFERENCES notices(notice_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS customer_faqs (
    faq_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(30) NOT NULL,
    audience VARCHAR(20) NOT NULL DEFAULT 'ALL',
    question VARCHAR(200) NOT NULL,
    answer TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_published BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS user_password_history (
    password_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    used_until TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_application_seeker ON applications(job_seeker_id,applied_at);
CREATE INDEX IF NOT EXISTS idx_application_job ON applications(job_id,status);
CREATE INDEX IF NOT EXISTS idx_employment_status ON employment_links(status,requested_at);
CREATE INDEX IF NOT EXISTS idx_inquiry_status ON inquiries(status,created_at);
CREATE INDEX IF NOT EXISTS idx_password_history_user_time ON user_password_history(user_id,used_until);


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
