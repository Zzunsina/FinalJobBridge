CREATE TABLE company_images (
    company_id BIGINT PRIMARY KEY,
    image_data MEDIUMBLOB NOT NULL,
    FOREIGN KEY (company_id) REFERENCES companies(company_id)
);
