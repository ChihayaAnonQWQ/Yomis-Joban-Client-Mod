# Runs the PIDS preset parser check against the freshly built module.
#
#   powershell -NoProfile -File tools\run-pids-check.ps1
#
# Works in both Windows PowerShell 5.1 and PowerShell 7.
# Exits non-zero if the build or the check fails, so it is usable from CI.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$gradle = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8\bin\gradle.bat'
if (-not (Test-Path $gradle)) { throw "Gradle 8.8 not found at $gradle" }

Write-Host '== building ==' -ForegroundColor Cyan
& $gradle build -I tools/init-pids-check.gradle :common:dumpRuntimeClasspath --no-daemon --console=plain --max-workers=1
if ($LASTEXITCODE -ne 0) { throw "gradle build failed ($LASTEXITCODE)" }

$classpath = Get-Content 'common\build\runtime-classpath.txt' -Raw
$outDir = 'build\pids-check'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host '== compiling check ==' -ForegroundColor Cyan
& "$env:JAVA_HOME\bin\javac.exe" -nowarn -encoding UTF-8 -cp $classpath -d $outDir `
    'tools\checks\com\jsblock\pids\PIDSPresetCheck.java'
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }

Write-Host '== running check ==' -ForegroundColor Cyan
& "$env:JAVA_HOME\bin\java.exe" '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' `
    '-cp' "$classpath;$outDir" 'com.jsblock.pids.PIDSPresetCheck'
exit $LASTEXITCODE
