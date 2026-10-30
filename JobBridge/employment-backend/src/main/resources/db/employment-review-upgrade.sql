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
