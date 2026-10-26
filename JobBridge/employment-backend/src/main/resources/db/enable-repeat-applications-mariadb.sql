-- Manual MariaDB deployment only. Back up the existing database first.
-- Keep a non-unique lookup/FK index; pending duplicates are serialized by the job row lock.
ALTER TABLE applications ADD INDEX IF NOT EXISTS ix_application_job_seeker(job_id,job_seeker_id);
SET @jobbridge_old_unique = (
 SELECT index_name FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name='applications' AND non_unique=0 AND index_name<>'PRIMARY'
 GROUP BY index_name
 HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) IN ('job_id,job_seeker_id','job_seeker_id,job_id')
 LIMIT 1
);
SET @jobbridge_update_sql = IF(@jobbridge_old_unique IS NULL, 'SELECT 1',
 CONCAT('ALTER TABLE applications DROP INDEX `',REPLACE(@jobbridge_old_unique,'`','``'),'`'));
PREPARE jobbridge_statement FROM @jobbridge_update_sql;
EXECUTE jobbridge_statement;
DEALLOCATE PREPARE jobbridge_statement;
