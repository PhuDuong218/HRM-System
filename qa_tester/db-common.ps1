$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$props=@{}
Get-Content (Join-Path $root 'HRM_System/src/main/resources/application-local.properties') | ForEach-Object { if($_ -match '^([^#=]+)=(.*)$'){$props[$matches[1].Trim()]=$matches[2].Trim()} }
$url=$props['spring.datasource.url'];$hostName='localhost';$portNumber=1433
if($url -match 'jdbc:sqlserver://([^:;]+)(?::(\d+))?'){$hostName=$matches[1];if($matches[2]){$portNumber=[int]$matches[2]}}
$builder=New-Object System.Data.SqlClient.SqlConnectionStringBuilder
$builder['Data Source']="$hostName,$portNumber";$builder['Initial Catalog']='hrm_test';$builder['User ID']=$props['spring.datasource.username'];$builder['Password']=$props['spring.datasource.password'];$builder['Encrypt']=$false;$builder['TrustServerCertificate']=$true;$builder['Connect Timeout']=8
$connection=New-Object System.Data.SqlClient.SqlConnection $builder.ConnectionString
$connection.Open()
$guard=$connection.CreateCommand();$guard.CommandText='SELECT DB_NAME()';if($guard.ExecuteScalar() -ne 'hrm_test'){$connection.Dispose();throw 'Refusing any database except hrm_test'}
