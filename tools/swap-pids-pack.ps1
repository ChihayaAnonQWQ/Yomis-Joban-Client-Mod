# Replaces the enabled CRT PIDS resource pack with the script-fixed copy.
#
#   powershell -NoProfile -File tools\swap-pids-pack.ps1
#
# Minecraft holds the pack zip open while it runs, so the game must be closed first.
# The original is kept as "<pack>.original-backup"; the fixed copy is the "<pack>.new"
# archive produced alongside it.
param(
	[string]$GameDir = 'D:\Minecraft\HZYMTR\versions\1.20.1-NanbinYMTR',
	[string]$PackName = 'HZYMTR CRT Pids1.0.zip'
)

$ErrorActionPreference = 'Stop'
$packs = Join-Path $GameDir 'resourcepacks'
$pack = Join-Path $packs $PackName
$fixed = "$pack.new"
$backup = "$pack.original-backup"

if (-not (Test-Path $fixed)) { throw "Fixed pack not found: $fixed" }
if (-not (Test-Path $backup)) {
	Copy-Item $pack $backup -Force
	Write-Host "Backed up the original to $(Split-Path $backup -Leaf)"
}

# Fails loudly if the game still has the zip open, rather than half-swapping.
try {
	Remove-Item $pack -Force -ErrorAction Stop
} catch {
	throw "Cannot replace the pack - is Minecraft still running? ($($_.Exception.Message))"
}
Move-Item $fixed $pack -Force

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($pack)
try {
	$entry = $zip.Entries | Where-Object { $_.FullName -eq 'assets/nanbin/pids/script/crt_pids_1.js' }
	if (-not $entry) { throw "The swapped pack has no crt_pids_1.js" }
	$backslashes = ($zip.Entries | Where-Object { $_.FullName -match '\\' }).Count
	Write-Host "Swapped. crt_pids_1.js is $($entry.Length) bytes ($(if ($entry.Length -gt 4000) { 'fixed' } else { 'STILL THE ORIGINAL' })); $backslashes entries use backslashes."
} finally {
	$zip.Dispose()
}
Write-Host "Start Minecraft to pick it up."
