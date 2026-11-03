$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$props=@{}
Get-Content (Join-Path $root 'HRM_System/src/main/resources/application-local.properties') | ForEach-Object { if($_ -match '^([^#=]+)=(.*)$'){$props[$matches[1].Trim()]=$matches[2].Trim()} }
$url=$props['spring.datasource.url'];$hostName='localhost';$portNumber=1433
if($url -match 'jdbc:sqlserver://([^:;]+)(?::(\d+))?'){$hostName=$matches[1];if($matches[2]){$portNumber=[int]$matches[2]}}
$builder=New-Object System.Data.SqlClient.SqlConnectionStringBuilder
$builder['Data Source']="$hostName,$portNumber";$builder['Initial Catalog']='hrm_test';$builder['User ID']=$props['spring.datasource.username'];$builder['Password']=$props['spring.datasource.password'];$builder['Encrypt']=$false;$builder['TrustServerCertificate']=$true;$builder['Connect Timeout']=8
$connection=New-Object System.Data.SqlClient.SqlConnection $builder.ConnectionString
try {
 $connection.Open();$cmd=$connection.CreateCommand();$cmd.CommandText="SELECT DB_NAME() AS database_name; SELECT COUNT(*) AS tables_count FROM sys.tables; SELECT name FROM sys.tables ORDER BY name";$reader=$cmd.ExecuteReader();$result=@();do{$rows=@();while($reader.Read()){$obj=@{};for($i=0;$i -lt $reader.FieldCount;$i++){$obj[$reader.GetName($i)]=$reader.GetValue($i)};$rows+=$obj};$result+=,@($rows)}while($reader.NextResult());$reader.Close();@{connected=$true;results=$result}|ConvertTo-Json -Depth 6
} catch { @{connected=$false;errorType=$_.Exception.GetType().Name;sqlError=$_.Exception.InnerException.Number;note='Connection to hrm_test failed; credentials and server hidden.'}|ConvertTo-Json } finally {$connection.Dispose()}
