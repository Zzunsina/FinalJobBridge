-- Apply once to existing databases. Preserves existing banner values.
ALTER TABLE job_postings ADD COLUMN IF NOT EXISTS banner_image_url MEDIUMTEXT NULL;
ALTER TABLE job_postings MODIFY COLUMN banner_image_url MEDIUMTEXT NULL;
