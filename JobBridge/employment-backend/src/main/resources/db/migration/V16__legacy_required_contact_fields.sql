-- Existing installations may have nullable legacy contact columns.
ALTER TABLE users MODIFY COLUMN phone VARCHAR(30) NOT NULL;
ALTER TABLE companies MODIFY COLUMN business_number VARCHAR(20) NOT NULL;
