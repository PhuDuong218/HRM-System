$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $classpathFile = 'target/duong-demo-classpath.txt'
    & .\mvnw.cmd test-compile dependency:build-classpath '-DincludeScope=test' "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    # A Java argument file avoids the Windows command-line length limit.
    $classpath = 'target/test-classes;target/classes;' + (Get-Content $classpathFile -Raw).Trim()
    $classpath = $classpath.Replace('\', '/')
    $argsFile = 'target/duong-demo-java.args'
    $lines = @('-cp', ('"' + $classpath + '"'), 'com.hrm.duong.PostmanDemoServer')
    [System.IO.File]::WriteAllLines((Join-Path (Get-Location) $argsFile), $lines, [System.Text.UTF8Encoding]::new($false))
    & java "@$argsFile"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
