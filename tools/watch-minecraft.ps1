# Watches the live Minecraft log for anything related to YJCM / the new PIDS layout engine.
#
#   powershell -NoProfile -File tools\watch-minecraft.ps1 -GameDir "D:\...\1.20.1-NanbinYMTR"
#
# Tails latest.log from its current end, prints only matching lines, and exits when the
# game process is gone, a crash report appears, or the timeout elapses.
param(
	[string]$GameDir = 'D:\Minecraft\HZYMTR\versions\1.20.1-NanbinYMTR',
	[int]$TimeoutSeconds = 7200,
	[int]$PollMilliseconds = 1000
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

Write-Stamp "watch started"
Write-Stamp "log       : $logPath"
Write-Stamp "crashes   : $crashDir"
Write-Stamp "timeout   : ${TimeoutSeconds}s"

if (-not (Test-Path $logPath)) { Write-Stamp "ERROR: log not found"; exit 2 }

$offset = (Get-Item $logPath).Length
Write-Stamp "starting at byte offset $offset"
Write-Output ('-' * 70)

$existingCrashes = @()
if (Test-Path $crashDir) { $existingCrashes = Get-ChildItem $crashDir -Filter '*.txt' | Select-Object -ExpandProperty Name }

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$sinceHeartbeat = 0

while ((Get-Date) -lt $deadline) {
	Start-Sleep -Milliseconds $PollMilliseconds
	$sinceHeartbeat += $PollMilliseconds

	# Exit once the game is gone (with a short grace period so the tail drains).
	$game = Get-Process -Name 'javaw', 'java' -ErrorAction SilentlyContinue |
		Where-Object { $_.WorkingSet64 -gt 300MB }
	if (-not $game) {
		Start-Sleep -Seconds 3
	}

	if (Test-Path $logPath) {
		$size = (Get-Item $logPath).Length
		if ($size -lt $offset) {
			Write-Stamp "log rotated (size $size < offset $offset), resetting"
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
				if ($line -and $line -match $pattern) {
					# Strip the noisy full stack-trace lines' leading whitespace but keep them.
					Write-Output $line.TrimEnd()
				}
			}
		}
	}

	# Surface any brand-new crash report immediately.
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

	if (-not $game -and $sinceHeartbeat -gt 5000) {
		Write-Output ('-' * 70)
		Write-Stamp "game process no longer running; watch ending"
		exit 0
	}

	if ($sinceHeartbeat -ge 120000) {
		$sinceHeartbeat = 0
		$alive = if ($game) { "game running" } else { "no game process" }
		Write-Stamp "watching... ($alive, log at $offset bytes)"
	}
}

Write-Stamp "timeout reached; watch ending"
exit 0
