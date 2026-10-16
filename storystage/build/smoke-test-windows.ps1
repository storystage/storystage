# Starts the packaged StoryStage console launcher and checks it is still running
# after start-up, with the version line printed and no exception in its output.
param(
  [Parameter(Mandatory = $true)][string]$AppDir,
  [int]$Seconds = 90
)

$ErrorActionPreference = 'Stop'
$log = Join-Path (Get-Location) 'target/smoke-test.log'
$err = Join-Path (Get-Location) 'target/smoke-test.err.log'
$exe = Join-Path $AppDir 'StoryStage-console.exe'
if (-not (Test-Path $exe)) { throw "Launcher not found: $exe" }

$proc = Start-Process -FilePath $exe -PassThru -RedirectStandardOutput $log -RedirectStandardError $err
Start-Sleep -Seconds $Seconds
$alive = -not $proc.HasExited
if ($alive) { Stop-Process -Id $proc.Id -Force }

Get-Content $err -ErrorAction SilentlyContinue | Add-Content $log
$output = Get-Content $log -Raw -ErrorAction SilentlyContinue
Write-Host '----- StoryStage output -----'
Write-Host $output
Write-Host '-----------------------------'

if (-not $alive) { throw "StoryStage exited during start-up (exit code $($proc.ExitCode))" }
if ($output -notmatch 'version:') { throw 'StoryStage did not print its version line' }
if ($output -match 'Exception in thread "main"|JavaFX runtime components are missing') {
  throw 'StoryStage reported a start-up error'
}
Write-Host "StoryStage was still running after $Seconds seconds."
