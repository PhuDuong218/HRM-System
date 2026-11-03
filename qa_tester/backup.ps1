. (Join-Path $PSScriptRoot 'db-common.ps1')
try {
 $cmd=$connection.CreateCommand();$cmd.CommandTimeout=300;$cmd.CommandText="SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'))";$folder=[string]$cmd.ExecuteScalar()
 if(-not $folder){throw 'Backup directory unavailable'}
 $file=$folder.TrimEnd('\')+'\QA_TESTER_hrm_test_'+(Get-Date -Format yyyyMMdd_HHmmss)+'.bak';$safe=$file.Replace("'","''")
 $cmd.CommandText="BACKUP DATABASE [hrm_test] TO DISK=N'$safe' WITH COPY_ONLY,CHECKSUM; RESTORE VERIFYONLY FROM DISK=N'$safe' WITH CHECKSUM";$null=$cmd.ExecuteNonQuery()
 @{database='hrm_test';copyOnly=$true;verifyOnly='PASS';backupPath=$file;restore='RESTORE DATABASE hrm_test FROM DISK=<backupPath> WITH REPLACE only after stopping all test clients; restore not executed.'}|ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'evidence/backup.json')
 Write-Output 'hrm_test COPY_ONLY + CHECKSUM backup verified.'
} finally {$connection.Dispose()}
