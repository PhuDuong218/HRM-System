[CmdletBinding()]
param(
    [ValidateSet('test','postman','stop')][string]$Mode = 'test',
    [ValidateRange(1,120)][int]$DurationMinutes = 30,
    [switch]$Offline,
    [string]$JavaHome,
    [string]$MavenCommand,
    [string]$MavenRepository
)

$ErrorActionPreference = 'Stop'
$taskProject = $PSScriptRoot
$taskOriginalJava = $env:JAVA_HOME
$taskOriginalPath = $env:PATH
$taskExit = 1
$taskStarted = Get-Date

try {
    if ($Mode -eq 'stop') {
        $taskTarget = Join-Path $taskProject 'target'
        New-Item -ItemType Directory -Path $taskTarget -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $taskTarget 'g1-postman.stop') -Value 'stop'
        Write-Host 'Stop signal sent to the G1 Postman test server.'
        exit 0
    }

    $taskUrl = $env:SPRING_DATASOURCE_URL
    $taskLocal = Join-Path $taskProject 'src/main/resources/application-local.properties'
    if (-not $taskUrl -and (Test-Path -LiteralPath $taskLocal)) {
        foreach ($taskLine in Get-Content -LiteralPath $taskLocal) {
            if ($taskLine -match '^\s*spring\.datasource\.url\s*=\s*(.+)$') { $taskUrl = $Matches[1].Trim() }
        }
    }
    if (-not $taskUrl -or $taskUrl -notmatch '(?i)(?:^|;)databaseName=hrm_test(?:;|$)') {
        throw 'G1 runner requires databaseName=hrm_test. Configure application-local.properties or SPRING_DATASOURCE_URL.'
    }

    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    elseif (-not $env:JAVA_HOME -and $env:ProgramFiles) {
        $taskJavaRoot = Join-Path $env:ProgramFiles 'Java'
        if (Test-Path -LiteralPath $taskJavaRoot) {
            $taskJdks = @(Get-ChildItem -LiteralPath $taskJavaRoot -Directory | Where-Object {
                Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe')
            })
            $taskJdk = $taskJdks | Where-Object Name -Match '^jdk-21(?:\D|$)' | Select-Object -First 1
            if (-not $taskJdk) { $taskJdk = $taskJdks | Sort-Object Name -Descending | Select-Object -First 1 }
            if ($taskJdk) { $env:JAVA_HOME = $taskJdk.FullName }
        }
    }
    if ($env:JAVA_HOME) {
        if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) { throw 'JAVA_HOME must point to a JDK.' }
        $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
    }

    $taskUserProfile = [Environment]::GetFolderPath('UserProfile')
    if (-not $MavenCommand) {
        $taskInstalled = Get-Command mvn.cmd -ErrorAction SilentlyContinue
        if ($taskInstalled) { $MavenCommand = $taskInstalled.Source }
        else {
            $taskCache = Join-Path $taskUserProfile '.m2/wrapper/dists'
            if (Test-Path -LiteralPath $taskCache) {
                $taskMaven = Get-ChildItem -LiteralPath $taskCache -Recurse -Filter mvn.cmd -File |
                    Sort-Object FullName -Descending | Select-Object -First 1
                if ($taskMaven) { $MavenCommand = $taskMaven.FullName }
            }
            if (-not $MavenCommand) { $MavenCommand = Join-Path $taskProject 'mvnw.cmd' }
        }
    }
    if (-not $MavenRepository) { $MavenRepository = Join-Path $taskUserProfile '.m2/repository' }
    $taskArguments = @("-Dmaven.repo.local=$MavenRepository", '-Dspring.jpa.show-sql=false')
    if ($Offline) { $taskArguments += '-o' }
    if ($Mode -eq 'postman') {
        $taskArguments += @('-Dtest=G1PostmanServer', '-Dg1.postman=true', "-Dg1.postman.minutes=$DurationMinutes")
        Write-Host 'Starting TEST-ONLY server on http://127.0.0.1:2006 (hrm_test).'
        Write-Host 'Wait for G1_POSTMAN_READY, then import target/g1-postman-runtime.postman_environment.json.'
    } else {
        $taskArguments += '-Dtest=G1*Test,Employee*Test,!EmployeeQualificationServiceTest,DepartmentHeadIntegrationTest,AssignmentLockConcurrencyTest'
    }
    $taskArguments += 'test'
    Push-Location -LiteralPath $taskProject
    try { & $MavenCommand @taskArguments; $taskExit = $LASTEXITCODE }
    finally { Pop-Location }

    if ($Mode -eq 'test') {
        $taskReportDirectory = Join-Path $taskProject 'target/surefire-reports'
        $taskRows = @()
        foreach ($taskFile in Get-ChildItem -LiteralPath $taskReportDirectory -Filter 'TEST-*.xml' -File) {
            if ($taskFile.LastWriteTime -lt $taskStarted.AddSeconds(-2)) { continue }
            [xml]$taskReport = [System.IO.File]::ReadAllText($taskFile.FullName)
            foreach ($taskCase in $taskReport.DocumentElement.SelectNodes('testcase')) {
                $taskStatus = 'PASS'
                if ($taskCase.SelectSingleNode('failure') -or $taskCase.SelectSingleNode('error')) { $taskStatus = 'FAIL' }
                elseif ($taskCase.SelectSingleNode('skipped')) { $taskStatus = 'SKIP' }
                $taskRows += [pscustomobject]@{ Status=$taskStatus; Test=$taskCase.GetAttribute('name'); Class=$taskCase.GetAttribute('classname') }
            }
        }
        if ($taskRows.Count -eq 0) { throw 'No fresh test reports found; do not treat this as a passing run.' }
        $taskRows | Export-Csv -LiteralPath (Join-Path $taskProject 'target/g1-test-summary.csv') -NoTypeInformation -Encoding UTF8
        $taskFailed = @($taskRows | Where-Object Status -EQ 'FAIL').Count
        $taskSkipped = @($taskRows | Where-Object Status -EQ 'SKIP').Count
        Write-Host "G1: $($taskRows.Count) tests; $taskFailed failed; $taskSkipped skipped."
        if ($taskFailed -gt 0 -or $taskSkipped -gt 0) { $taskExit = 1 }
    }
} catch {
    Write-Host ('G1 runner stopped: ' + $_.Exception.Message) -ForegroundColor Red
    $taskExit = 1
} finally {
    $env:JAVA_HOME = $taskOriginalJava
    $env:PATH = $taskOriginalPath
}
exit $taskExit
