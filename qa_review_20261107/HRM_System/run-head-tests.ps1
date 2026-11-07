[CmdletBinding()]
param(
    [switch]$All,
    [switch]$Offline,
    [string]$JavaHome,
    [string]$MavenCommand,
    [string]$MavenRepository
)

# Adds a convenient runner only. Does not edit application properties or Java code.
$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
$originalJavaHome = $env:JAVA_HOME
$originalPath = $env:PATH
$exitCode = 1
$started = Get-Date

try {
    $configFile = Join-Path $projectDirectory 'src/main/resources/application-local.properties'
    $databaseUrl = $env:SPRING_DATASOURCE_URL
    if (-not $databaseUrl -and (Test-Path -LiteralPath $configFile)) {
        foreach ($line in Get-Content -LiteralPath $configFile) {
            if ($line -match '^\s*spring\.datasource\.url\s*=\s*(.+)$') {
                $databaseUrl = $Matches[1].Trim()
            }
        }
    }
    if (-not $databaseUrl -or $databaseUrl -notmatch '(?i)(?:^|;)databaseName=([^;]+)') {
        throw 'Cannot find a test database URL. Configure application-local.properties or SPRING_DATASOURCE_URL first.'
    }
    $databaseName = $Matches[1]
    if ($databaseName -notmatch '(?i)test') {
        throw 'Refusing to run: database name must contain test. Use a dedicated SQL Server test database with the HRM schema.'
    }
    Write-Host "Database preflight: $databaseName (test schema must already exist)."

    if ($JavaHome) {
        $env:JAVA_HOME = $JavaHome
    } elseif (-not $env:JAVA_HOME -and $env:ProgramFiles) {
        $javaDirectory = Join-Path $env:ProgramFiles 'Java'
        if (Test-Path -LiteralPath $javaDirectory) {
            $jdks = @(Get-ChildItem -LiteralPath $javaDirectory -Directory |
                Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe') })
            $jdk = $jdks | Where-Object Name -Match '^jdk-21(?:\D|$)' | Select-Object -First 1
            if (-not $jdk) { $jdk = $jdks | Sort-Object Name -Descending | Select-Object -First 1 }
            if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
        }
    }
    if ($env:JAVA_HOME) {
        if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
            throw 'JAVA_HOME must point to a JDK directory containing bin/javac.exe.'
        }
        $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
    }

    if (-not $MavenCommand) {
        $installed = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if ($installed) { $MavenCommand = $installed.Source }
        else {
            $profileDirectory = [Environment]::GetFolderPath('UserProfile')
            $cachedMaven = Join-Path $profileDirectory '.m2/wrapper/dists'
            if (Test-Path -LiteralPath $cachedMaven) {
                $candidate = Get-ChildItem -LiteralPath $cachedMaven -Filter mvn.cmd -Recurse -File |
                    Sort-Object FullName -Descending | Select-Object -First 1
                if ($candidate) { $MavenCommand = $candidate.FullName }
            }
            if (-not $MavenCommand) { $MavenCommand = Join-Path $projectDirectory 'mvnw.cmd' }
        }
    }
    if (-not (Test-Path -LiteralPath $MavenCommand)) {
        throw 'Maven command not found. Pass -MavenCommand with the full path to mvn.cmd.'
    }

    $arguments = @('-Dspring.jpa.show-sql=false')
    if ($MavenRepository) { $arguments += "-Dmaven.repo.local=$MavenRepository" }
    if ($Offline) { $arguments += '-o' }
    if (-not $All) {
        $arguments += '-Dtest=DepartmentHeadIntegrationTest,AssignmentLockConcurrencyTest'
    }
    $arguments += 'test'

    Push-Location -LiteralPath $projectDirectory
    try {
        & $MavenCommand @arguments
        $exitCode = $LASTEXITCODE
    } finally { Pop-Location }

    $reportDirectory = Join-Path $projectDirectory 'target/surefire-reports'
    $rows = @()
    if (Test-Path -LiteralPath $reportDirectory) {
        foreach ($file in Get-ChildItem -LiteralPath $reportDirectory -Filter 'TEST-*.xml' -File) {
            if ($file.LastWriteTime -lt $started.AddSeconds(-2)) { continue }
            [xml]$report = Get-Content -Raw -LiteralPath $file.FullName
            $suite = $report.DocumentElement
            if (-not $All -and $suite.GetAttribute('name') -notmatch '\.(DepartmentHeadIntegrationTest|AssignmentLockConcurrencyTest)$') { continue }
            foreach ($case in $suite.SelectNodes('testcase')) {
                $status = 'PASS'
                if ($case.SelectSingleNode('failure') -or $case.SelectSingleNode('error')) { $status = 'FAIL' }
                elseif ($case.SelectSingleNode('skipped')) { $status = 'SKIP' }
                $rows += [pscustomobject]@{
                    Status = $status
                    Test = $case.GetAttribute('name')
                    Class = $case.GetAttribute('classname')
                    Seconds = $case.GetAttribute('time')
                }
            }
        }
    }
    if ($rows.Count -gt 0) {
        $rows | Format-Table Status, Test, Seconds -AutoSize | Out-Host
        $summaryPath = Join-Path $projectDirectory 'target/head-test-summary.csv'
        $rows | Export-Csv -LiteralPath $summaryPath -NoTypeInformation -Encoding UTF8
        $failed = @($rows | Where-Object Status -EQ 'FAIL').Count
        $skipped = @($rows | Where-Object Status -EQ 'SKIP').Count
        Write-Host "Results: $($rows.Count) tests; $failed failed; $skipped skipped."
        Write-Host "Summary: $summaryPath"
        if ($failed -gt 0 -and $exitCode -eq 0) { $exitCode = 1 }
    } elseif ($exitCode -eq 0) {
        Write-Error 'Maven returned success but no fresh test reports were found.'
    }
} catch {
    Write-Host ('Test runner stopped: ' + $_.Exception.Message) -ForegroundColor Red
    $exitCode = 1
} finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:PATH = $originalPath
}
exit $exitCode
