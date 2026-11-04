param([string]$Server = 'localhost,1433')
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $backendRoot
$suffix = (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + [guid]::NewGuid().ToString('N').Substring(0,6)
$testDatabase = 'hrm_task01_review_' + $suffix
$logDirectory = Join-Path $backendRoot ('target/task01-sql-review/' + $testDatabase)
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$results = [System.Collections.Generic.List[object]]::new()

function Invoke-ReviewSql {
    param([string]$Name, [string]$Sql, [string]$Database = $testDatabase, [int]$ExpectedError = 0)
    $inputPath = Join-Path $logDirectory ($Name + '.sql')
    $outputPath = Join-Path $logDirectory ($Name + '.log')
    [System.IO.File]::WriteAllText($inputPath, $Sql, [System.Text.UTF8Encoding]::new($false))
    $arguments = @('-S',$Server,'-E','-I','-l','10','-t','120','-b','-r','1','-f','65001',
        '-d',$Database,'-v',('TargetDatabase=' + $Database),'AllowMainDatabase=0','-i',$inputPath)
    # Windows PowerShell wraps native stderr as ErrorRecord, even SQL informational messages.
    # Judge the explicit exit code + expected SQL error, rather than PowerShell's stderr policy.
    $savedErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & sqlcmd @arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedErrorPreference }
    $combined = ($output | ForEach-Object { $_.ToString() }) -join "`n"
    [System.IO.File]::WriteAllText($outputPath, $combined, [System.Text.UTF8Encoding]::new($false))
    $passed = if ($ExpectedError -eq 0) { $exitCode -eq 0 } else {
        $exitCode -ne 0 -and $combined -match ('Msg\s+' + $ExpectedError + '\b')
    }
    $results.Add([pscustomobject]@{ name=$Name; passed=$passed; expectedError=$ExpectedError; exitCode=$exitCode })
    if (-not $passed) { throw "SQL case $Name failed; see $outputPath" }
    Write-Host ('PASS ' + $Name)
    return $combined
}
function Read-Scalar {
    param([string]$Database,[string]$Query)
    $value = & sqlcmd -S $Server -E -l 10 -t 120 -b -d $Database -Q "SET NOCOUNT ON; $Query" -h -1 -W
    if ($LASTEXITCODE -ne 0) { throw 'Read-only SQL query failed.' }
    return (($value | ForEach-Object { $_.Trim() }) -join '').Trim()
}
function Assert-Equal {
    param([string]$Name,[string]$Actual,[string]$Expected)
    if ($Actual -cne $Expected) { throw "Assertion failed: $Name" }
    $results.Add([pscustomobject]@{ name=$Name; passed=$true; expectedError=0; exitCode=0 })
    Write-Host ('PASS ' + $Name)
}
$coreColumns = 'd.decision_id,d.employee_id,d.decision_number,d.decision_type,d.content,d.issued_date,d.issued_by,d.approved_by,d.approved_at,d.approval_status,d.payroll_effect,d.amount,d.apply_date,d.created_at,d.updated_at,d.created_by,d.updated_by'
$coreHash = "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT $coreColumns FROM dbo.employee_decisions d ORDER BY d.decision_id FOR JSON PATH,INCLUDE_NULL_VALUES)),2);"
$fullHash = "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.employee_decisions ORDER BY decision_id FOR JSON PATH,INCLUDE_NULL_VALUES)),2);"
$auditCount = "SELECT COUNT(*) FROM dbo.audit_logs WHERE correlation_id='FD409DE2-E66A-40B5-84DA-012851149011';"
$sourceBefore = Read-Scalar 'hrm_db' $fullHash
$sourceAuditBefore = Read-Scalar 'hrm_db' 'SELECT COUNT(*) FROM dbo.audit_logs;'

# A NEW copy, never overwrites hrm_test or hrm_db. No REPLACE, DROP or create_database.sql.
$copySql = @"
SET NOCOUNT ON;
IF DB_ID(N'$testDatabase') IS NOT NULL THROW 51790,N'Test database already exists. Never overwrite it.',1;
DECLARE @backup nvarchar(4000)=CONVERT(nvarchar(3500),SERVERPROPERTY('InstanceDefaultBackupPath'))+N'\hrm_db_task01_$suffix.bak';
DECLARE @data nvarchar(4000)=CONVERT(nvarchar(3500),SERVERPROPERTY('InstanceDefaultDataPath'))+N'$testDatabase.mdf';
DECLARE @log nvarchar(4000)=CONVERT(nvarchar(3500),SERVERPROPERTY('InstanceDefaultLogPath'))+N'$testDatabase`_log.ldf';
BACKUP DATABASE [hrm_db] TO DISK=@backup WITH COPY_ONLY,CHECKSUM;
RESTORE VERIFYONLY FROM DISK=@backup WITH CHECKSUM;
RESTORE DATABASE [$testDatabase] FROM DISK=@backup WITH MOVE N'hrm_db' TO @data,MOVE N'hrm_db_log' TO @log,RECOVERY,CHECKSUM;
SELECT @backup AS verified_backup_path,N'$testDatabase' AS test_database;
"@
$null = Invoke-ReviewSql '00_clone' $copySql 'master'
$baseline = Read-Scalar $testDatabase $coreHash
$null = Invoke-ReviewSql '01_main_guard' ':r database/deployments/011_reviewed_decisions.sql' 'hrm_db' 51710
$null = Invoke-ReviewSql '02_legacy_blocks_schema' ':r database/migrations/011_decision_rules.sql' $testDatabase 51701
Assert-Equal '02_no_schema_left_behind' (Read-Scalar $testDatabase "SELECT COALESCE(CONVERT(varchar(10),COL_LENGTH('dbo.employee_decisions','discipline_form')),'ABSENT');") 'ABSENT'
$null = Invoke-ReviewSql '03_content_conflict' @"
BEGIN TRANSACTION;
UPDATE dbo.employee_decisions SET content=N'Quyet dinh cach chuc' WHERE decision_id=4;
GO
:r database/deployments/011_reviewed_decisions.sql
"@ $testDatabase 51713
$null = Invoke-ReviewSql '04_closed_payroll' @"
BEGIN TRANSACTION;
DECLARE @batch bigint,@period bigint,@employee bigint,@actor bigint;
SELECT TOP(1) @batch=b.batch_id,@period=b.period_id,@employee=x.employee_id
FROM dbo.payroll_batches b JOIN dbo.payroll_details x ON x.batch_id=b.batch_id ORDER BY b.batch_id,x.employee_id;
IF @batch IS NULL THROW 51791,N'No payroll fixture available.',1;
SELECT @actor=issued_by FROM dbo.employee_decisions WHERE decision_id=5;
UPDATE dbo.payroll_periods SET period_start='20261001',period_end='20261031' WHERE period_id=@period;
UPDATE dbo.payroll_batches SET status='DA_CHOT',locked_by=@actor,locked_at=SYSUTCDATETIME() WHERE batch_id=@batch;
UPDATE dbo.employee_decisions SET employee_id=@employee WHERE decision_id=5;
GO
:r database/deployments/011_reviewed_decisions.sql
"@ $testDatabase 51712
Assert-Equal '04_negative_cases_rolled_back' (Read-Scalar $testDatabase $coreHash) $baseline
$null = Invoke-ReviewSql '05_deploy' ':r database/deployments/011_reviewed_decisions.sql'
$after = Read-Scalar $testDatabase $fullHash
Assert-Equal '05_four_audits' (Read-Scalar $testDatabase $auditCount) '4'
$null = Invoke-ReviewSql '06_idempotent_deploy' ':r database/deployments/011_reviewed_decisions.sql'
Assert-Equal '06_same_rows_and_versions' (Read-Scalar $testDatabase $fullHash) $after
Assert-Equal '06_no_duplicate_audits' (Read-Scalar $testDatabase $auditCount) '4'
$null = Invoke-ReviewSql '07_check_discipline_deduct' "BEGIN TRAN; UPDATE dbo.employee_decisions SET payroll_effect='DEDUCT',amount=1,apply_date=issued_date WHERE decision_id=2; ROLLBACK;" $testDatabase 547
$null = Invoke-ReviewSql '08_check_missing_form' "BEGIN TRAN; UPDATE dbo.employee_decisions SET discipline_form=NULL WHERE decision_id=2; ROLLBACK;" $testDatabase 547
$null = Invoke-ReviewSql '09_check_delay_7' "BEGIN TRAN; UPDATE dbo.employee_decisions SET discipline_form='KEO_DAI_NANG_LUONG',salary_increase_delay_months=7 WHERE decision_id=2; ROLLBACK;" $testDatabase 547
$null = Invoke-ReviewSql '10_valid_delay_6' "BEGIN TRAN; UPDATE dbo.employee_decisions SET discipline_form='KEO_DAI_NANG_LUONG',salary_increase_delay_months=6 WHERE decision_id=2; ROLLBACK;"
$null = Invoke-ReviewSql '11_valid_compensation' "BEGIN TRAN; UPDATE dbo.employee_decisions SET decision_type='BOI_THUONG',discipline_form=NULL,violation_date=NULL,payroll_effect='DEDUCT',amount=10,apply_date=issued_date WHERE decision_id=4; ROLLBACK;"
$null = Invoke-ReviewSql '12_check_reward_deduct' "BEGIN TRAN; UPDATE dbo.employee_decisions SET payroll_effect='DEDUCT' WHERE decision_id=1; ROLLBACK;" $testDatabase 547
$null = Invoke-ReviewSql '13_rollback_drift' @"
BEGIN TRAN;
UPDATE dbo.employee_decisions SET content=content+N' changed after deployment' WHERE decision_id=2;
GO
:r database/deployments/011_reviewed_decisions.rollback.sql
"@ $testDatabase 51721
$null = Invoke-ReviewSql '14_rollback_new_compensation' @"
BEGIN TRAN;
UPDATE dbo.employee_decisions SET decision_type='BOI_THUONG',payroll_effect='DEDUCT' WHERE decision_id=1;
GO
:r database/deployments/011_reviewed_decisions.rollback.sql
"@ $testDatabase 51702
# A successful compensating UPDATE generates a fresh row_version; compare restored business fields.
$null = Invoke-ReviewSql '15_rollback' ':r database/deployments/011_reviewed_decisions.rollback.sql'
Assert-Equal '15_restored_all_original_business_fields' (Read-Scalar $testDatabase $coreHash) $baseline
Assert-Equal '15_schema_removed' (Read-Scalar $testDatabase "SELECT COALESCE(CONVERT(varchar(10),COL_LENGTH('dbo.employee_decisions','discipline_form')),'ABSENT');") 'ABSENT'
Assert-Equal '15_audits_retained' (Read-Scalar $testDatabase $auditCount) '8'
$null = Invoke-ReviewSql '16_idempotent_rollback' ':r database/deployments/011_reviewed_decisions.rollback.sql'
Assert-Equal '16_no_duplicate_rollback_audits' (Read-Scalar $testDatabase $auditCount) '8'
$null = Invoke-ReviewSql '17_reapply_for_review' ':r database/deployments/011_reviewed_decisions.sql'
Assert-Equal '17_new_audited_cycle' (Read-Scalar $testDatabase $auditCount) '12'
Assert-Equal '18_main_rows_untouched' (Read-Scalar 'hrm_db' $fullHash) $sourceBefore
Assert-Equal '18_main_audits_untouched' (Read-Scalar 'hrm_db' 'SELECT COUNT(*) FROM dbo.audit_logs;') $sourceAuditBefore
$report = [pscustomobject]@{ database=$testDatabase; server=$Server; finishedAt=(Get-Date -Format o); results=$results; sourceDecisionHash=$sourceBefore; logs=$logDirectory }
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $logDirectory 'results.json') -Encoding utf8
Write-Host ('All ' + $results.Count + ' checks passed. Review database: ' + $testDatabase)
Write-Host ('Logs: ' + $logDirectory)
