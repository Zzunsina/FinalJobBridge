CREATE TABLE IF NOT EXISTS interview_attendance_requests (
 request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 interview_id BIGINT NOT NULL UNIQUE,
 requester_user_id BIGINT NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
 message VARCHAR(3000) NOT NULL,
 evidence_data MEDIUMBLOB, evidence_type VARCHAR(80), evidence_name VARCHAR(200),
 requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 company_due_at TIMESTAMP NOT NULL,
 company_reason VARCHAR(3000), company_responded_at TIMESTAMP NULL,
 escalation_reason VARCHAR(3000), escalated_at TIMESTAMP NULL,
 admin_reason VARCHAR(3000), reviewed_by BIGINT, reviewed_at TIMESTAMP NULL,
 FOREIGN KEY(interview_id) REFERENCES interviews(interview_id),
 FOREIGN KEY(requester_user_id) REFERENCES users(user_id),
 FOREIGN KEY(reviewed_by) REFERENCES users(user_id),
 CHECK(status IN ('PENDING','CONFIRMED','DECLINED','ESCALATED','ADMIN_CONFIRMED','ADMIN_DECLINED'))
);
CREATE TABLE IF NOT EXISTS interview_reviews (
 review_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 interview_id BIGINT NOT NULL UNIQUE,
 author_user_id BIGINT NOT NULL,
 company_id BIGINT NOT NULL,
 rating INT NOT NULL, difficulty INT NOT NULL,
 process VARCHAR(200) NOT NULL, questions TEXT NOT NULL, content TEXT NOT NULL,
 visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(interview_id) REFERENCES interviews(interview_id),
 FOREIGN KEY(author_user_id) REFERENCES users(user_id),
 FOREIGN KEY(company_id) REFERENCES companies(company_id),
 CHECK(rating BETWEEN 1 AND 5), CHECK(difficulty BETWEEN 1 AND 5),
 CHECK(visibility IN ('PUBLISHED','HIDDEN','DELETED'))
);
CREATE TABLE IF NOT EXISTS member_reports (
 report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
 reporter_user_id BIGINT NOT NULL,
 target_type VARCHAR(20) NOT NULL, target_id BIGINT NOT NULL,
 target_user_id BIGINT NOT NULL,
 reason VARCHAR(40) NOT NULL, details TEXT NOT NULL, target_snapshot MEDIUMTEXT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING', resolution VARCHAR(30), resolution_reason TEXT,
 reviewed_by BIGINT, reviewed_at TIMESTAMP NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(reporter_user_id,target_type,target_id),
 FOREIGN KEY(reporter_user_id) REFERENCES users(user_id),
 FOREIGN KEY(target_user_id) REFERENCES users(user_id),
 FOREIGN KEY(reviewed_by) REFERENCES users(user_id),
 CHECK(target_type IN ('APPLICANT','REVIEW')), CHECK(status IN ('PENDING','RESOLVED'))
);
CREATE TABLE IF NOT EXISTS review_business_holidays (holiday_date DATE PRIMARY KEY);

CREATE TABLE IF NOT EXISTS interview_review_moderation (
 review_id BIGINT PRIMARY KEY,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 revision INT NOT NULL DEFAULT 1,
 reason VARCHAR(3000), reviewed_by BIGINT, reviewed_at TIMESTAMP NULL,
 submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(review_id) REFERENCES interview_reviews(review_id),
 FOREIGN KEY(reviewed_by) REFERENCES users(user_id),
 CHECK(status IN ('PENDING','APPROVED','REJECTED'))
);
