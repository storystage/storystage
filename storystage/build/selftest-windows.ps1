# Runs StoryStage-selftest.exe, which opens a new Grass world and drives the Prompter's tools
# against it without Claude, then checks it printed SELFTEST PASS and exited with 0.
param(
  [Parameter(Mandatory = $true)][string]$AppDir,
  [int]$TimeoutSeconds = 420
)

$ErrorActionPreference = 'Stop'
$log = Join-Path (Get-Location) 'target/selftest.log'
$err = Join-Path (Get-Location) 'target/selftest.err.log'
$exe = Join-Path $AppDir 'StoryStage-selftest.exe'
if (-not (Test-Path $exe)) { throw "Self-test launcher not found: $exe" }

$proc = Start-Process -FilePath $exe -PassThru -RedirectStandardOutput $log -RedirectStandardError $err
$finished = $proc.WaitForExit($TimeoutSeconds * 1000)
if (-not $finished) { Stop-Process -Id $proc.Id -Force }

Get-Content $err -ErrorAction SilentlyContinue | Add-Content $log
$output = Get-Content $log -Raw -ErrorAction SilentlyContinue
Write-Host '----- self-test output -----'
Get-Content $log -ErrorAction SilentlyContinue | Where-Object { $_ -match '^\[selftest\]|Exception|Error' } | ForEach-Object { Write-Host $_ }
Write-Host '----------------------------'

if (-not $finished) { throw "The self-test didn't finish within $TimeoutSeconds seconds" }
if ($proc.ExitCode -ne 0 -or $output -notmatch 'SELFTEST PASS') {
  throw "The self-test failed (exit code $($proc.ExitCode))"
}
Write-Host 'The Prompter self-test passed.'
