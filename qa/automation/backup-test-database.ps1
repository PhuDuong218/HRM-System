[CmdletBinding()]
param([switch]$LocalIntegrated)
$ErrorActionPreference='Stop'
$qaRoot=Split-Path $PSScriptRoot -Parent
$repoRoot=Split-Path $qaRoot -Parent
if(-not $LocalIntegrated){
$properties=@{}
Get-Content -LiteralPath (Join-Path $repoRoot 'HRM_System/src/main/resources/application-local.properties') | ForEach-Object {
 if($_ -match '^\s*([^#!\s][^=]*)=(.*)$'){$properties[$matches[1].Trim()]=$matches[2].Trim()}
}
$url=$env:SPRING_DATASOURCE_URL
if(-not $url){$url=$properties['spring.datasource.url']}
if($url -notmatch '^jdbc:sqlserver://([^;]+)'){throw 'Datasource URL cannot be resolved safely'}
$server=$matches[1] -replace ':',','
if($url -notmatch '(?i)databaseName=hrm_test(?:;|$)'){throw 'Refusing: configured datasource is not hrm_test'}
$login=$env:SPRING_DATASOURCE_USERNAME
if(-not $login){$login=$properties['spring.datasource.username']}
$secret=$env:SPRING_DATASOURCE_PASSWORD
if(-not $secret){$secret=$properties['spring.datasource.password']}
if(-not $secret -or $secret.StartsWith('${')){throw 'Password is missing or unresolved; do not print it'}
}
$oldPassword=$env:SQLCMDPASSWORD
try {
 if($LocalIntegrated){$argsBase=@('-S','localhost','-E','-C','-d','hrm_test','-b','-l','15','-W','-h','-1')}else{
 $env:SQLCMDPASSWORD=$secret
 $argsBase=@('-S',$server,'-U',$login,'-C','-d','hrm_test','-b','-l','15','-W','-h','-1')}
 $actual=(& sqlcmd @argsBase -Q 'SET NOCOUNT ON; SELECT DB_NAME();' 2>&1 | Out-String).Trim()
 if($LASTEXITCODE -ne 0 -or $actual -ne 'hrm_test'){throw 'Database verification failed; no backup or tests started'}
 $folder=(& sqlcmd @argsBase -Q "SET NOCOUNT ON; SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'));" 2>&1 | Out-String).Trim()
 if($LASTEXITCODE -ne 0 -or -not $folder -or $folder -eq 'NULL'){throw 'Server default backup folder unavailable'}
 $stamp=Get-Date -Format 'yyyyMMdd_HHmmss'
 $backup=$folder.TrimEnd('\','/')+'\QA_hrm_test_'+$stamp+'_'+[guid]::NewGuid().ToString('N').Substring(0,8)+'.bak'
 $sqlPath=$backup.Replace("'","''")
 $result=& sqlcmd @argsBase -t 300 -Q "BACKUP DATABASE [hrm_test] TO DISK=N'$sqlPath' WITH COPY_ONLY,CHECKSUM; RESTORE VERIFYONLY FROM DISK=N'$sqlPath' WITH CHECKSUM;" 2>&1
 if($LASTEXITCODE -ne 0){throw 'Backup or RESTORE VERIFYONLY failed; do not start write tests'}
 $evidence=[ordered]@{database='hrm_test';server=$(if($LocalIntegrated){'localhost integrated'}else{'remote (redacted)'});checkedAt=(Get-Date).ToString('o');backupPath=$backup;copyOnly=$true;checksum=$true;verifyOnly='PASS';details=($result|Out-String)}
 $evidence | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $qaRoot $(if($LocalIntegrated){'evidence/database-backup-local.json'}else{'evidence/database-backup.json'})) -Encoding utf8
 Write-Output 'hrm_test verified. COPY_ONLY backup + checksum RESTORE VERIFYONLY passed. Evidence saved; credentials were not exported.'
} finally {$env:SQLCMDPASSWORD=$oldPassword}
