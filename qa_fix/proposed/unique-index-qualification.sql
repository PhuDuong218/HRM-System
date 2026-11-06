-- PROPOSAL ONLY. Do not run without schema approval and review of existing duplicates.
-- Canonical key MUST be produced from the exact application rule:
-- employee + level + NFC(trim(major)).lower(ROOT) + NFC(trim(institution)).lower(ROOT)
-- + graduation year + NFC(trim(certificate_number)).lower(ROOT).
-- Serialize fields with length/null delimiters before SHA-256; never concatenate ambiguously.
-- SQL Server alone does not provide the same NFC normalization as java.text.Normalizer.
-- Stage 1: nullable materialized key; deploy canonical-key writer before requiring backfill.
USE hrm_center_full;
GO
IF COL_LENGTH('dbo.employee_qualifications','duplicate_key') IS NULL
 ALTER TABLE dbo.employee_qualifications ADD duplicate_key binary(32) NULL;
GO
-- Stage 2 (manual, separate approval): compute keys with Java normalizer; report existing
-- duplicates including QA6-*; do NOT delete, merge or silently modify any historic rows.
-- Repository treats NULL is_deleted as active too. Before enabling the filtered index,
-- separately approve normalizing legacy NULL flags to 0; this proposal does not run that UPDATE.
-- Stop deployment until duplicates are resolved with owners. A SHA collision must be
-- distinguished from semantic duplicates by comparing normalized source fields.
SELECT employee_id,duplicate_key,COUNT(*) records
FROM dbo.employee_qualifications
WHERE (is_deleted=0 OR is_deleted IS NULL) AND duplicate_key IS NOT NULL
GROUP BY employee_id,duplicate_key HAVING COUNT(*)>1;
GO
-- Stage 3: run only after approved backfill and conflict resolution.
CREATE UNIQUE INDEX UX_employee_qualification_duplicate_key
 ON dbo.employee_qualifications(employee_id,duplicate_key)
 WHERE is_deleted=0 AND duplicate_key IS NOT NULL;
GO
-- Existing rows with NULL duplicate_key remain outside this index until backfilled.
-- Application serialization by employee remains required throughout migration.
