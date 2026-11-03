. (Join-Path (Split-Path $PSScriptRoot -Parent) 'db-common.ps1')
$connection.Dispose()
$env:HRM_VERIFIED_BUGS_IT='true'
$env:HRM_VERIFIED_BUGS_URL=$url -replace '(?i)databaseName=[^;]+','databaseName=hrm_test'
$env:HRM_VERIFIED_BUGS_USER=$props['spring.datasource.username'];$env:HRM_VERIFIED_BUGS_PASSWORD=$props['spring.datasource.password']
$env:HRM_VERIFIED_BUGS_LOGIN_USER='admin.sample'
$readme=Get-Content (Join-Path $root 'HRM_System/database/README_TAI_KHOAN.md') -Raw
$env:HRM_VERIFIED_BUGS_LOGIN_PASSWORD=[regex]::Match($readme,'`([^`]+)`:').Groups[1].Value
$env:JWT_SECRET=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$env:JAVA_HOME='C:\Program Files\Java\jdk-23';$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
Push-Location (Join-Path $root 'HRM_System')
try {
 & 'C:\Users\GPA\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -o '-Dtest=VerifiedBugsSqlServerIntegrationTest,AuthLogoutRegressionTest,LeaveBalanceScopeRegressionTest,GlobalExceptionHandlerMergeTest,JwtDatabaseTransactionIntegrationTest,AdminAccessApiIntegrationTest,PersonnelSearchTest,PersonnelSearchControllerTest,PayrollManagementAuthorizationTest,PayrollMealExemptionTest,EmployeeDetailServiceTest' '-DargLine=-javaagent:C:\Users\GPA\.m2\repository\org\mockito\mockito-core\5.23.0\mockito-core-5.23.0.jar' test 2>&1 | ForEach-Object {"$_" -replace 'jdbc:(sqlserver|h2):[^\s]+','[CONNECTION REDACTED]' -replace '(?i)(Using generated security password:).*','$1 ***' -replace 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[JWT REDACTED]'} | Out-File (Join-Path $PSScriptRoot 'backend-regression.log') -Encoding utf8
 $code=$LASTEXITCODE
}finally{Pop-Location}
Write-Output ('Backend regression exit='+$code)
exit $code
