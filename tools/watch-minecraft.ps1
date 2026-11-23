# Watches the live Minecraft log for anything related to YJCM / the new PIDS layout engine.
#
#   powershell -NoProfile -File tools\watch-minecraft.ps1 -GameDir "D:\...\1.20.1-NanbinYMTR"
#
# Tails latest.log from its current end, prints only matching lines, and exits when the
# game has been gone for a sustained period, a crash report appears, or the timeout elapses.
param(
	[string]$GameDir = 'D:\Minecraft\HZYMTR\versions\1.20.1-NanbinYMTR',
	[int]$TimeoutSeconds = 10800,
	[int]$PollMilliseconds = 1000,
	[int]$ExitAfterGameGoneSeconds = 20
)

$ErrorActionPreference = 'Continue'
$logPath = Join-Path $GameDir 'logs\latest.log'
$crashDir = Join-Path $GameDir 'crash-reports'

# Anything worth surfacing: our own logger, our packages, PIDS rendering, and hard errors.
$pattern = 'Joban Client|com\.jsblock|jsblock|PIDS|RenderPIDSBase|renderLayout|PIDSLayout|' +
           'SEVERE|ERROR|FATAL|Exception|Caused by|crash|Mixin|NoSuchMethod|NoClassDefFound|' +
           'AbstractMethodError|ClassCastException|NullPointerException'

function Write-Stamp([string]$text) {
	Write-Output ("[{0:HH:mm:ss}] {1}" -f (Get-Date), $text)
}

# Identify the game by its --gameDir argument. The previous version of this script matched any
# java process over 300 MB, which reported "game gone" while a freshly started JVM was still
# small -- so it quit during a restart. Startup footprint is not a reliable signal.
function Test-GameRunning {
	$procs = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction SilentlyContinue
	foreach ($p in $procs) {
		if ($p.CommandLine -and $p.CommandLine.Contains($GameDir)) { return $true }
	}
	return $false
}

Write-Stamp "watch started"
Write-Stamp "log       : $logPath"
Write-Stamp "crashes   : $crashDir"
Write-Stamp "timeout   : ${TimeoutSeconds}s"
Write-Stamp "game now  : $(if (Test-GameRunning) { 'running' } else { 'not detected' })"

if (-not (Test-Path $logPath)) { Write-Stamp "ERROR: log not found"; exit 2 }

$offset = (Get-Item $logPath).Length
Write-Stamp "starting at byte offset $offset"
Write-Output ('-' * 70)

$existingCrashes = @()
if (Test-Path $crashDir) { $existingCrashes = Get-ChildItem $crashDir -Filter '*.txt' | Select-Object -ExpandProperty Name }

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$sinceHeartbeat = 0
$goneFor = 0
$tick = 0

while ((Get-Date) -lt $deadline) {
	Start-Sleep -Milliseconds $PollMilliseconds
	$sinceHeartbeat += $PollMilliseconds
	$tick++

	# --- new log content -------------------------------------------------
	if (Test-Path $logPath) {
		$size = (Get-Item $logPath).Length
		if ($size -lt $offset) {
			Write-Stamp "log rotated (size $size < offset $offset), resetting -- game likely restarted"
			$offset = 0
		}
		if ($size -gt $offset) {
			$stream = [System.IO.File]::Open($logPath, 'Open', 'Read', 'ReadWrite')
			try {
				$null = $stream.Seek($offset, 'Begin')
				$count = [int]($size - $offset)
				$buffer = New-Object byte[] $count
				$read = $stream.Read($buffer, 0, $count)
				$offset = $stream.Position
				$text = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $read)
			} finally {
				$stream.Close()
			}

			foreach ($line in ($text -split "`r?`n")) {
				if ($line -and $line -match $pattern) { Write-Output $line.TrimEnd() }
			}
		}
	}

	# --- crash reports ---------------------------------------------------
	if (Test-Path $crashDir) {
		foreach ($c in (Get-ChildItem $crashDir -Filter '*.txt' -ErrorAction SilentlyContinue)) {
			if ($existingCrashes -notcontains $c.Name) {
				Write-Output ''
				Write-Stamp "*** NEW CRASH REPORT: $($c.Name) ***"
				Get-Content $c.FullName -Encoding UTF8 -TotalCount 60 | ForEach-Object { Write-Output $_ }
				Write-Output ''
				$existingCrashes += $c.Name
			}
		}
	}

	# --- liveness (cheap check every ~5s) --------------------------------
	if ($tick % 5 -eq 0) {
		if (Test-GameRunning) {
			$goneFor = 0
		} else {
			$goneFor += 5
			if ($goneFor -ge $ExitAfterGameGoneSeconds) {
				Write-Output ('-' * 70)
				Write-Stamp "game has been gone for ${goneFor}s; watch ending"
				exit 0
			}
		}
	}

	if ($sinceHeartbeat -ge 120000) {
		$sinceHeartbeat = 0
		$alive = if (Test-GameRunning) { 'game running' } else { 'no game process' }
		Write-Stamp "watching... ($alive, log at $offset bytes)"
	}
}

Write-Stamp "timeout reached; watch ending"
exit 0
