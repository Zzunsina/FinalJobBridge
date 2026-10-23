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
