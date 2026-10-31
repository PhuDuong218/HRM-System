[CmdletBinding()]
param([Parameter(Mandatory)][string]$SqlFile,[Parameter(Mandatory)][string]$OutputFile)
$ErrorActionPreference='Stop'
$qaRoot=Split-Path $PSScriptRoot -Parent
$repoRoot=Split-Path $qaRoot -Parent
$properties=@{}
Get-Content (Join-Path $repoRoot 'HRM_System/src/main/resources/application-local.properties') | ForEach-Object {if($_ -match '^\s*([^#!\s][^=]*)=(.*)$'){$properties[$matches[1].Trim()]=$matches[2].Trim()}}
$url=$env:SPRING_DATASOURCE_URL;if(-not $url){$url=$properties['spring.datasource.url']}
if($url -notmatch '(?i)databaseName=hrm_test(?:;|$)'){throw 'Refusing non-test database'}
if($url -notmatch '^jdbc:sqlserver://([^;]+)'){throw 'Unsupported datasource URL'}
$server=$matches[1] -replace ':',','
$login=$env:SPRING_DATASOURCE_USERNAME;if(-not $login){$login=$properties['spring.datasource.username']}
$oldPassword=$env:SQLCMDPASSWORD
try{
 $env:SQLCMDPASSWORD=$env:SPRING_DATASOURCE_PASSWORD;if(-not $env:SQLCMDPASSWORD){$env:SQLCMDPASSWORD=$properties['spring.datasource.password']}
 $guard="SET XACT_ABORT ON; SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_WARNINGS ON; SET ANSI_PADDING ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF; SET NOCOUNT ON; IF DB_NAME()<>'hrm_test' THROW 51000,'QA database guard',1; "
 $query=$guard+(Get-Content -LiteralPath $SqlFile -Raw)
 $output=& sqlcmd -S $server -U $login -C -d hrm_test -b -l 15 -y 0 -w 65535 -Q $query 2>&1
 if($LASTEXITCODE -ne 0){$safe=($output|Out-String).Replace($env:SQLCMDPASSWORD,'***');throw ('QA SQL execution failed: '+$safe.Substring(0,[Math]::Min(600,$safe.Length)))}
 $text=$output -join '';  $first=$text.IndexOf('{'); $last=$text.LastIndexOf('}'); if($first -ge 0 -and $last -gt $first){$text=$text.Substring($first,$last-$first+1); $null=$text|ConvertFrom-Json}; $text | Out-File -LiteralPath $OutputFile -Encoding utf8
 Write-Output 'QA database evidence saved; no credentials exported.'
}finally{$env:SQLCMDPASSWORD=$oldPassword}
