-- DB ADMIN: READ-ONLY CHECKS. Confirm approved host/database first.
SELECT DATABASE(), VERSION();
SHOW CREATE TABLE employment_db.users;
SHOW CREATE TABLE employment_db.companies;
SHOW CREATE TABLE employment_db.user_signup_consents;
-- Expected uq_users_phone: NON_UNIQUE=0; exactly one row;
-- COLUMN_NAME=phone; SEQ_IN_INDEX=1; SUB_PART IS NULL.
SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='employment_db' AND TABLE_NAME='users'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
SELECT installed_rank, version, description, type, script, checksum, success
FROM employment_db.flyway_schema_history ORDER BY installed_rank;
SELECT version, COUNT(*) AS history_rows, SUM(success=0) AS failed_rows
FROM employment_db.flyway_schema_history
WHERE version IN ('14','15','16') GROUP BY version ORDER BY version;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='employment_db'
AND ((TABLE_NAME='users' AND COLUMN_NAME IN ('login_id','birth_date','gender','phone'))
OR (TABLE_NAME='companies' AND COLUMN_NAME='business_number'))
ORDER BY TABLE_NAME, COLUMN_NAME;
-- Read ../flyway-startup-recovery.md before deciding any recovery action.
-- Do not run repair/clean or delete indexes based only on these query results.