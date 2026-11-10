[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$serverRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$keyFile = Join-Path $env:LOCALAPPDATA 'HRM-Center/jwt-secret.txt'
if (!(Test-Path -LiteralPath $keyFile)) {
    throw 'Không tìm thấy khóa JWT riêng trong LocalAppData/HRM-Center.'
}
$env:JWT_SECRET = [IO.File]::ReadAllText($keyFile).Trim()
Push-Location $serverRoot
try {
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $cachedMaven = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($maven) { $command = $maven.Source }
    elseif ($cachedMaven) { $command = $cachedMaven.FullName }
    else { $command = Join-Path $serverRoot 'mvnw.cmd' }
    $repository = Join-Path $env:USERPROFILE '.m2/repository'
    & $command "-Dmaven.repo.local=$repository" spring-boot:run '-Dspring-boot.run.profiles=local,hrm-db'
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
