-- Add the applicant-visible guidance without modifying private interview memos.
ALTER TABLE interviews ADD COLUMN IF NOT EXISTS applicant_guidance VARCHAR(2000);
