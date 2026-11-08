param(
    [string]$Server = 'localhost,1433',
    [string]$Database = 'hrm_task01_review_20261103_180233_3505ce'
)
$ErrorActionPreference = 'Stop'
if ($Database -notmatch '^hrm_task01_review_[0-9]{8}_[0-9]{6}_[a-f0-9]{6}$') {
    throw 'Only an isolated task-1 review copy is allowed; never hrm_db or hrm_test.'
}
$backendRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $backendRoot
$logDirectory = Join-Path $backendRoot ('target/task03-sql-review/' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$results = [System.Collections.Generic.List[object]]::new()
function Invoke-Case {
    param([string]$Name,[string]$Sql,[int]$ExpectedError = 0)
    $inputPath = Join-Path $logDirectory ($Name + '.sql')
    [System.IO.File]::WriteAllText($inputPath,$Sql,[System.Text.UTF8Encoding]::new($false))
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & sqlcmd -S $Server -E -I -b -l 10 -t 60 -f 65001 -d $Database -i $inputPath 2>&1
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
    $combined = ($output | ForEach-Object { $_.ToString() }) -join "`n"
    [System.IO.File]::WriteAllText((Join-Path $logDirectory ($Name + '.log')),$combined,[System.Text.UTF8Encoding]::new($false))
    $passed = if ($ExpectedError -eq 0) { $exitCode -eq 0 } else {
        $exitCode -ne 0 -and $combined -match ('Msg\s+' + $ExpectedError + '\b')
    }
    $results.Add([pscustomobject]@{name=$Name;passed=$passed;expectedError=$ExpectedError;exitCode=$exitCode})
    if (-not $passed) { throw "SQL case $Name failed; see $logDirectory" }
    Write-Host ('PASS ' + $Name)
}

Invoke-Case '00_preflight' @'
IF COL_LENGTH('dbo.contracts','termination_reason_type') IS NOT NULL
    EXEC(N'IF EXISTS(SELECT 1 FROM dbo.contracts WHERE termination_reason_type IS NOT NULL)
        THROW 51939,N''Review copy has termination data. Do not overwrite it for tests.'',1;');
'@
Invoke-Case '01_apply_twice' @'
SET NOCOUNT ON;
-- Compare every pre-existing column, including row_version, before/after two schema runs.
DECLARE @columns nvarchar(max);
SELECT @columns=STRING_AGG(CONVERT(nvarchar(max),QUOTENAME(name)),',') WITHIN GROUP(ORDER BY column_id)
FROM sys.columns WHERE object_id=OBJECT_ID('dbo.contracts') AND name<>'termination_reason_type';
CREATE TABLE #Before013(hash_value varbinary(32),columns_sql nvarchar(max));
DECLARE @snapshot nvarchar(max)=N'INSERT #Before013 SELECT HASHBYTES(''SHA2_256'',(SELECT '+@columns+N' FROM dbo.contracts ORDER BY contract_id FOR JSON PATH,INCLUDE_NULL_VALUES)),@columnList';
EXEC sys.sp_executesql @snapshot,N'@columnList nvarchar(max)',@columnList=@columns;
GO
:r database/migrations/013_contract_termination_reason.sql
GO
:r database/migrations/013_contract_termination_reason.sql
GO
DECLARE @columns nvarchar(max)=(SELECT columns_sql FROM #Before013);
EXEC(N'IF (SELECT hash_value FROM #Before013)<>HASHBYTES(''SHA2_256'',(SELECT '+@columns+N' FROM dbo.contracts ORDER BY contract_id FOR JSON PATH,INCLUDE_NULL_VALUES)) THROW 51932,N''Schema migration changed existing contract data.'',1;');
IF EXISTS(SELECT 1 FROM dbo.contracts WHERE termination_reason_type IS NOT NULL) THROW 51933,N'Legacy reasons must remain NULL.',1;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.contracts')
    AND name='CK_contracts_termination_reason_013' AND is_disabled=0 AND is_not_trusted=0)
    THROW 51934,N'CHECK is not enabled/trusted.',1;
'@
foreach ($reason in @('HET_HAN','THOA_THUAN','NLD_DON_PHUONG','NSDLD_DON_PHUONG','SA_THAI')) {
    Invoke-Case ('02_valid_' + $reason) "BEGIN TRAN; UPDATE dbo.contracts SET termination_reason_type='$reason' WHERE contract_id=(SELECT MIN(contract_id) FROM dbo.contracts); ROLLBACK;"
}
Invoke-Case '03_invalid_reason' "BEGIN TRAN; UPDATE dbo.contracts SET termination_reason_type='UNKNOWN' WHERE contract_id=(SELECT MIN(contract_id) FROM dbo.contracts); ROLLBACK;" 547
Invoke-Case '04_rollback_protects_used_reason' @'
BEGIN TRAN;
UPDATE dbo.contracts SET termination_reason_type='THOA_THUAN' WHERE contract_id=(SELECT MIN(contract_id) FROM dbo.contracts);
GO
:r database/migrations/013_contract_termination_reason.rollback.sql
'@ 51931
Invoke-Case '05_guard_failure_keeps_schema' @'
IF COL_LENGTH('dbo.contracts','termination_reason_type') IS NULL THROW 51935,N'Guard failure removed schema.',1;
IF EXISTS(SELECT 1 FROM dbo.contracts WHERE termination_reason_type IS NOT NULL) THROW 51936,N'Test write was not rolled back.',1;
'@
Invoke-Case '06_rollback_twice' @'
:r database/migrations/013_contract_termination_reason.rollback.sql
GO
:r database/migrations/013_contract_termination_reason.rollback.sql
GO
IF COL_LENGTH('dbo.contracts','termination_reason_type') IS NOT NULL THROW 51937,N'Rollback did not remove column.',1;
'@
Invoke-Case '07_reapply_for_review' @'
:r database/migrations/013_contract_termination_reason.sql
GO
SELECT name,is_disabled,is_not_trusted FROM sys.check_constraints
WHERE parent_object_id=OBJECT_ID('dbo.contracts') AND name='CK_contracts_termination_reason_013';
'@
[pscustomobject]@{database=$Database;finishedAt=(Get-Date -Format o);results=$results;logs=$logDirectory} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $logDirectory 'results.json') -Encoding utf8
Write-Host ('All ' + $results.Count + ' SQL cases passed. Main database was not used. Logs: ' + $logDirectory)
