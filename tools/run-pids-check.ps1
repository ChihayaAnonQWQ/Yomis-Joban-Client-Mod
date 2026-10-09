# Builds the mod and runs every headless check under tools/checks.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-pids-check.ps1
#
# Two checks, both compiled with plain javac against :common's runtime classpath:
#
#   PixelationCheck  checks the offscreen target's sizing rules, including the aspect
#                    correction a pack's dot resolution goes through.
#   PIDSPresetCheck  parses the shipped JSON presets and verifies the display-row mapping
#                    against the built-in renderers' advance rule.
#   ScriptApiCheck   runs real JCM 2.x PIDS scripts through the real wrappers with a
#                    recording render context. Everything except the actual GPU draw runs
#                    for real — Rhino compilation, include(), the globals, and the whole
#                    Text/Texture/Rectangle builder chain — so a wrapper method this port
#                    is missing fails here exactly as it would in game.
#   ScriptShutterCheck  checks which Java classes a script may reach.
#   ScriptPathCheck     checks which files a script may reach: include() and
#                       Texture.texture() must not resolve outside the script's own pack.
#   ScriptCanvasCheck   checks the runtime canvas: that drawing it paints pixels, that a failure
#                       to upload it degrades, and that close()/reload release the texture.
#   ScriptCaseCheck     checks the spelling tolerance for script names: a pack that stored
#                       Digital_Rail.js has to be reachable, and a capital must not be a way out of
#                       a pack.
#   ScriptHkrCheck      runs HKR's terminus rule -- the one that decides "not in service" from the
#                       panel's own index in the service's calling pattern -- against a real MTR
#                       world, at both ends of a three-station line. Pass -HkrPack to run the two
#                       shipped presets through it as well; without it the check runs an embedded
#                       preset that is that rule and nothing else.
#
# ScriptApiCheck is run against every script in -Script (default: the built-in
# jsblock:scripts/builtin/pids_1a.js) at several arrival counts, because a preset that
# only ever sees two trains hides the empty-platform and mixed-car-length bugs.
#
# Works in both Windows PowerShell 5.1 and PowerShell 7.
# Exits non-zero if the build or any check fails, so it is usable from CI.
#
# Running resource-pack presets kept outside the repository:
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\run-pids-check.ps1 `
#       -ResourceRoot ..\..\yjcm-recon\hkr `
#       -Script       ..\..\yjcm-recon\hkr\assets\jsblock\scripts\hkr_pids_default.js
#
# A resource root is any directory holding assets/<namespace>/...; the repository's own
# common/src/main/resources is always included, so jsblock:scripts/pids_util.js resolves.
param(
    # Extra preset scripts to run. Relative paths resolve against the repository root.
    [string[]]$Script = @(),

    # Extra resource roots, searched in addition to common/src/main/resources.
    [string[]]$ResourceRoot = @(),

    # A directory holding assets/jsblock/scripts/hkr_pids_default.js (an extracted copy of the
    # HKR pack's "lower" tree). Supplied, ScriptHkrCheck also runs HKR's two shipped presets, so
    # the pack's own wording is asserted and not only the rule's.
    [string]$HkrPack = '',

    # Skip the Gradle build and reuse the existing classes (useful when iterating on a check).
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

<#
  Exit code of the most recent Invoke-Native call.

  A PowerShell function returns everything written to its output stream, so a helper cannot
  both stream a native tool's stdout and `return` its exit code -- the code would be appended
  to the output and every comparison against 0 would fail. The code is therefore published
  through this variable while the tool's output keeps flowing to the caller's pipeline.

  Native tools also write notes and progress to stderr, and PowerShell turns each such line
  into an ErrorRecord -- which $ErrorActionPreference = 'Stop' treats as a terminating error,
  killing the run on javac's harmless deprecation note. The policy is relaxed for the duration
  of the call only.
#>
$NativeExitCode = 0
function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$Arguments = @()
    )
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $FilePath @Arguments
        $script:NativeExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
}

$gradle = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8\bin\gradle.bat'

if (-not $SkipBuild) {
    if (-not (Test-Path $gradle)) { throw "Gradle 8.8 not found at $gradle" }

    Write-Host '== building ==' -ForegroundColor Cyan
    Invoke-Native -FilePath $gradle -Arguments @(
        'build', '-I', 'tools/init-pids-check.gradle', ':common:dumpRuntimeClasspath',
        '--no-daemon', '--console=plain', '--max-workers=1'
    )
    if ($NativeExitCode -ne 0) { throw "gradle build failed ($NativeExitCode)" }
}

$classpath = (Get-Content 'common\build\runtime-classpath.txt' -Raw).Trim()
$outDir = 'build\pids-check'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$checkSources = @(
    'tools\checks\com\jsblock\pids\PIDSPresetCheck.java',
    'tools\checks\com\jsblock\pids\PixelationCheck.java',
    'tools\checks\com\jsblock\script\ScriptApiCheck.java',
    'tools\checks\com\jsblock\script\ScriptShutterCheck.java',
    'tools\checks\com\jsblock\script\ScriptPathCheck.java',
    'tools\checks\com\jsblock\script\ScriptCanvasCheck.java',
    'tools\checks\com\jsblock\script\FilesCheck.java',
    'tools\checks\com\jsblock\script\FilesCheckSupport.java',
    'tools\checks\com\jsblock\script\ScriptCaseCheck.java',
    'tools\checks\com\jsblock\script\ScriptHkrCheck.java'
)
foreach ($source in $checkSources) {
    if (-not (Test-Path $source)) { throw "check source missing: $source" }
}

Write-Host '== compiling checks ==' -ForegroundColor Cyan
Invoke-Native -FilePath "$env:JAVA_HOME\bin\javac.exe" -Arguments (@(
    '-nowarn', '-encoding', 'UTF-8', '-cp', $classpath, '-d', $outDir
) + $checkSources)
if ($NativeExitCode -ne 0) { throw "javac failed ($NativeExitCode)" }

$failures = @()

Write-Host ''
Write-Host '== component presets (PIDSPresetCheck) ==' -ForegroundColor Cyan
Invoke-Native -FilePath "$env:JAVA_HOME\bin\java.exe" -Arguments @(
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8',
    '-cp', "$classpath;$outDir", 'com.jsblock.pids.PIDSPresetCheck'
)
if ($NativeExitCode -ne 0) { $failures += "PIDSPresetCheck (exit $NativeExitCode)" }

# The sizing rules, checked without a GPU: the offscreen target's proportions have to be the
# canvas's, and a pack's resolution is the one input that can break that from a config file.
Write-Host ''
Write-Host '== pixelation sizing (PixelationCheck) ==' -ForegroundColor Cyan
Invoke-Native -FilePath "$env:JAVA_HOME\bin\java.exe" -Arguments @(
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8',
    '-cp', "$classpath;$outDir", 'com.jsblock.pids.PixelationCheck'
)
if ($NativeExitCode -ne 0) { $failures += "PixelationCheck (exit $NativeExitCode)" }

# The sandbox is checked before the presets: if the shutter is wrong, every script below is
# either blocked or unguarded, and the reason should be obvious from this block rather than
# inferred from sixteen script failures.
Write-Host ''
Write-Host '== script sandbox (ScriptShutterCheck) ==' -ForegroundColor Cyan
Invoke-Native -FilePath "$env:JAVA_HOME\bin\java.exe" -Arguments @(
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8',
    '-cp', "$classpath;$outDir", 'com.jsblock.script.ScriptShutterCheck'
)
if ($NativeExitCode -ne 0) { $failures += "ScriptShutterCheck (exit $NativeExitCode)" }

# What a script can make the engine read. The sandbox check above covers Java classes; this
# covers the files, which a script reaches through include() and Texture.texture(...) instead.
Write-Host ''
Write-Host '== script file access (ScriptPathCheck) ==' -ForegroundColor Cyan
Invoke-Native -FilePath "$env:JAVA_HOME\bin\java.exe" -Arguments @(
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8',
    '-cp', "$classpath;$outDir", 'com.jsblock.script.ScriptPathCheck'
)
if ($NativeExitCode -ne 0) { $failures += "ScriptPathCheck (exit $NativeExitCode)" }

# The canvas a script draws into at runtime: the drawing half runs for real (Java2D needs no GPU),
# the upload half degrades, and the release half is the one that decides whether a pack leaks.
Write-Host ''
Write-Host '== script canvas (ScriptCanvasCheck) ==' -ForegroundColor Cyan
Invoke-Native -FilePath "$env:JAVA_HOME\bin\java.exe" -Arguments @(
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8',
    '-cp', "$classpath;$outDir", 'com.jsblock.script.ScriptCanvasCheck'
)
if ($NativeExitCode -ne 0) { $failures += "ScriptCanvasCheck (exit $NativeExitCode)" }

# The Files global: the one part of the scripting surface that writes to the disk. A value saved
# has to be the value read back, a missing file has to answer null, and every shape of path escape
# has to be refused -- a PIDS preset arrives inside a resource pack, and a pack arrives with a
# server or a modpack.
Write-Host ''
Write-Host '== script file storage (FilesCheck) ==' -ForegroundColor Cyan
Invoke-Native -FilePath "$env:JAVA_HOME\bin\java.exe" -Arguments @(
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8',
    '-cp', "$classpath;$outDir", 'com.jsblock.script.FilesCheck'
)
if ($NativeExitCode -ne 0) { $failures += "FilesCheck (exit $NativeExitCode)" }

# Script reference spelling. Resource names are lower case in Minecraft and ResourceLocation
# refuses a capital outright, so a pack that names a file Digital_Rail.js -- met transit ships that
# name -- used to fail before any file was read. The tolerance lives here, next to the assertions
# that a capital is not a way to walk out of a pack.
Write-Host ''
Write-Host '== script reference spelling (ScriptCaseCheck) ==' -ForegroundColor Cyan
Invoke-Native -FilePath "$env:JAVA_HOME\bin\java.exe" -Arguments @(
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8',
    '-cp', "$classpath;$outDir", 'com.jsblock.script.ScriptCaseCheck'
)
if ($NativeExitCode -ne 0) { $failures += "ScriptCaseCheck (exit $NativeExitCode)" }

# HKR's terminus rule, on a real MTR world: the same panel preset has to say "not in service" at
# the last stop of the line and draw the real arrivals at a stop in the middle. Run last, because
# it is the only check that builds MTR's own client data.
$hkrScripts = @()
$hkrRoots = @()
if ($HkrPack -ne '') {
    if (-not (Test-Path $HkrPack)) { throw "HKR pack root missing: $HkrPack" }
    foreach ($name in @('hkr_pids_default.js', 'hkr_pids_platform.js')) {
        $candidate = Join-Path $HkrPack "assets\jsblock\scripts\$name"
        if (Test-Path $candidate) { $hkrScripts += $candidate }
    }
    $hkrRoots = @($HkrPack)
    if ($hkrScripts.Count -eq 0) {
        Write-Host "   no HKR preset found under $HkrPack" -ForegroundColor Yellow
    }
}
Write-Host ''
Write-Host '== HKR terminus rule (ScriptHkrCheck) ==' -ForegroundColor Cyan
Invoke-Native -FilePath "$env:JAVA_HOME\bin\java.exe" -Arguments (@(
    '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8',
    '-cp', "$classpath;$outDir", 'com.jsblock.script.ScriptHkrCheck'
) + $hkrScripts + $hkrRoots + @('common\src\main\resources'))
if ($NativeExitCode -ne 0) { $failures += "ScriptHkrCheck (exit $NativeExitCode)" }

# The built-in pids_1a.js is the script the mod ships, so it is the one that must never
# regress. Anything passed through -Script is run in addition to it.
$scriptUnderTest = @('common\src\main\resources\assets\jsblock\scripts\builtin\pids_1a.js') + $Script

$roots = @('common\src\main\resources') + $ResourceRoot
foreach ($root in $roots) {
    if (-not (Test-Path $root)) { throw "resource root missing: $root" }
}

# 0 arrivals is the empty-platform case JCM 2.x's own presets guard with `arrival == null`;
# 4 exceeds every PIDS block's row count, which is where row indexing bugs show up.
$arrivalCounts = @(0, 1, 2, 4)

foreach ($script in $scriptUnderTest) {
    if (-not (Test-Path $script)) { throw "script missing: $script" }

    foreach ($arrivals in $arrivalCounts) {
        Write-Host ''
        Write-Host "== script check: $script (arrivals=$arrivals) ==" -ForegroundColor Cyan
        Invoke-Native -FilePath "$env:JAVA_HOME\bin\java.exe" -Arguments (@(
            '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8',
            '-cp', "$classpath;$outDir",
            'com.jsblock.script.ScriptApiCheck',
            $script
        ) + $roots + @("--arrivals=$arrivals", '--iterations=2'))

        if ($NativeExitCode -ne 0) {
            $failures += "ScriptApiCheck $script arrivals=$arrivals (exit $NativeExitCode)"
        }
    }
}

Write-Host ''
if ($failures.Count -eq 0) {
    Write-Host 'RESULT: ALL CHECKS PASSED' -ForegroundColor Green
    exit 0
}

Write-Host "RESULT: $($failures.Count) FAILING CHECK(S)" -ForegroundColor Red
foreach ($failure in $failures) { Write-Host "  - $failure" -ForegroundColor Red }
exit 1
