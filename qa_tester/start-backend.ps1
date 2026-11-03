. (Join-Path $PSScriptRoot 'db-common.ps1')
$connection.Dispose()
$env:SPRING_DATASOURCE_URL=$url -replace '(?i)databaseName=[^;]+','databaseName=hrm_test'
$env:HRM_DB_URL=$env:SPRING_DATASOURCE_URL
$env:SPRING_DATASOURCE_USERNAME=$props['spring.datasource.username'];$env:SPRING_DATASOURCE_PASSWORD=$props['spring.datasource.password']
$env:JWT_SECRET=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$env:QA_RUNTIME_EVIDENCE=Join-Path $PSScriptRoot 'evidence/runtime.json'
$cp=(Join-Path $root 'HRM_System/target/classes')+';'+$PSScriptRoot+';'+(Get-Content (Join-Path $root 'qa/evidence/backend-classpath.txt') -Raw).Trim()
$rawOut=Join-Path $env:TEMP 'hrm-qa-tester-backend.out';$rawErr=Join-Path $env:TEMP 'hrm-qa-tester-backend.err'
& 'C:\Program Files\Java\jdk-23\bin\java.exe' -cp $cp QaBackendLauncher --server.port=2009 --spring.profiles.active=local,hrm-db --spring.jpa.show-sql=false 2>&1 | ForEach-Object {"$_" -replace 'jdbc:sqlserver://[^\s]+','[DATASOURCE REDACTED]' -replace '(?i)(Using generated security password:).*','$1 ***' -replace 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[JWT REDACTED]'} | Out-File (Join-Path $PSScriptRoot 'evidence/backend.log') -Encoding utf8
