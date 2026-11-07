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
        Set-Content -LiteralPath (Join-Path $taskTarget 'personnel-postman.stop') -Value 'stop'
        Write-Host 'Stop signal sent to the Personnel Postman test server.'
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
        throw 'Personnel runner requires databaseName=hrm_test. Configure application-local.properties or SPRING_DATASOURCE_URL.'
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
        $taskArguments += @('-Dtest=PersonnelPostmanServer', '-Dpersonnel.postman=true', "-Dpersonnel.postman.minutes=$DurationMinutes")
        Write-Host 'Starting TEST-ONLY server on http://127.0.0.1:2007 (hrm_test).'
        Write-Host 'Wait for PERSONNEL_POSTMAN_READY, then import target/personnel-postman-runtime.postman_environment.json.'
    } else {
        $taskArguments += '-Dtest=*Test,!EmployeeQualificationServiceTest,!QualificationLevelServiceTest'
    }
    if ($Mode -eq 'test') { $taskArguments += '-Dhrm.tests.allow-external-db=true' }
    if ($Mode -eq 'test') {
        # Rebuild compiled output only; preserve runtime reports, backups and review artifacts.
        $taskBuildRoot = [IO.Path]::GetFullPath((Join-Path $taskProject 'target'))
        foreach ($taskBuildName in @('classes','test-classes','maven-status')) {
            $taskBuildPath = [IO.Path]::GetFullPath((Join-Path $taskBuildRoot $taskBuildName))
            if ((Split-Path -Parent $taskBuildPath) -ne $taskBuildRoot) { throw 'Unsafe compiler output directory.' }
            if (Test-Path -LiteralPath $taskBuildPath) { Remove-Item -LiteralPath $taskBuildPath -Recurse -Force }
        }
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
        $taskRows | Export-Csv -LiteralPath (Join-Path $taskProject 'target/personnel-test-summary.csv') -NoTypeInformation -Encoding UTF8
        $taskFailed = @($taskRows | Where-Object Status -EQ 'FAIL').Count
        $taskSkipped = @($taskRows | Where-Object Status -EQ 'SKIP').Count
        Write-Host "Personnel: $($taskRows.Count) tests; $taskFailed failed; $taskSkipped skipped."
        if ($taskFailed -gt 0 -or $taskSkipped -gt 0) { $taskExit = 1 }
    }
} catch {
    Write-Host ('Personnel runner stopped: ' + $_.Exception.Message) -ForegroundColor Red
    $taskExit = 1
} finally {
    $env:JAVA_HOME = $taskOriginalJava
    $env:PATH = $taskOriginalPath
}
exit $taskExit
