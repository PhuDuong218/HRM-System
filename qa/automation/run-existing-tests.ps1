[CmdletBinding()]
param([string]$JavaHome='C:/Program Files/Java/jdk-23')
$ErrorActionPreference='Continue'
$qaRoot=Split-Path $PSScriptRoot -Parent
$repoRoot=Split-Path $qaRoot -Parent
foreach($file in @('database-backup.json','database-backup-local.json')){
 $proof=Join-Path $qaRoot ('evidence/'+$file)
 if(-not(Test-Path -LiteralPath $proof)){throw 'Required verified test database backup is missing'}
 $backup=Get-Content -LiteralPath $proof -Raw | ConvertFrom-Json
 if($backup.database -ne 'hrm_test' -or $backup.verifyOnly -ne 'PASS'){throw 'Invalid backup evidence'}
}
$env:JAVA_HOME=$JavaHome
$agent=Join-Path $env:USERPROFILE '.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
if(-not(Test-Path -LiteralPath $agent)){throw 'Cached Mockito agent not found'}
function Hide-Secrets([string]$line){
 $line=$line -replace 'jdbc:sqlserver://[^\s\]]+','[DATASOURCE REDACTED]'
 $line=$line -replace 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[JWT REDACTED]'
 $line=$line -replace '(?i)(Using generated security password:).*','$1 ***'
 $line=$line -replace '(?i)((?:password|jwt-secret|accessToken|refreshToken)\s*[=:]\s*)[^\s,}]+','$1***'
 return $line
}
$start=Get-Date
[ordered]@{started=$start.ToString('o');state='RUNNING';branch=(& git -C $repoRoot branch --show-current);command='mvnw -o test with cached Mockito javaagent';backgroundJobs='Existing test configuration; isolated databases backed up. Job behaviour assessed separately.'} | ConvertTo-Json | Set-Content (Join-Path $qaRoot 'evidence/existing-suite-run.json') -Encoding utf8
Push-Location (Join-Path $repoRoot 'HRM_System')
try {
 & ./mvnw.cmd -o test "-DargLine=-javaagent:$agent" 2>&1 | ForEach-Object {Hide-Secrets "$_"} | Out-File (Join-Path $qaRoot 'evidence/existing-suite.log') -Encoding utf8
 $result=$LASTEXITCODE
} finally {Pop-Location}
[ordered]@{started=$start.ToString('o');finished=(Get-Date).ToString('o');state='FINISHED';exitCode=$result;javaHome=$JavaHome;command='mvnw -o test with cached Mockito javaagent';backgroundJobs='Existing test configuration; see separate job assessment'} | ConvertTo-Json | Set-Content (Join-Path $qaRoot 'evidence/existing-suite-run.json') -Encoding utf8
Get-Content (Join-Path $qaRoot 'evidence/existing-suite.log') -Tail 22
exit $result
