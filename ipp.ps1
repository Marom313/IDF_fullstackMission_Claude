<#
  IPP command runner for Windows PowerShell. Same commands as the Makefile.
  Usage: ./ipp.ps1 <up|down|seed|verify|demo|logs|reset|ps> [service]
#>
param([Parameter(Position = 0)][string]$Cmd = "help", [Parameter(Position = 1)][string]$Arg = "")
# NOT "Stop": Windows PowerShell 5.1 turns docker progress output on stderr into a terminating
# NativeCommandError. We check $LASTEXITCODE after every native command instead (see Invoke-Native).
$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot
$env:COMPOSE_PARALLEL_LIMIT = "2"
$env:DOCKER_BUILDKIT = "1"
if (-not (Test-Path .env)) { Copy-Item .env.example .env }

function Invoke-Bash([string]$script) {
  $bash = (Get-Command bash -ErrorAction SilentlyContinue).Source
  if (-not $bash) { $bash = "C:\Program Files\Git\bin\bash.exe" }
  & $bash $script
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

function Invoke-Native([scriptblock]$cmd) {
  & $cmd 2>&1 | ForEach-Object { "$_" }
  if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: failed (exit $LASTEXITCODE)" -ForegroundColor Red; exit $LASTEXITCODE }
}

switch ($Cmd) {
  "up"     { Invoke-Native { docker compose --profile core --profile ingest --profile observability up -d --build --wait --wait-timeout 900 } }
  "down"   { Invoke-Native { docker compose --profile core --profile ingest --profile observability down } }
  "reset"  { Invoke-Native { docker compose --profile core --profile ingest --profile observability down -v --remove-orphans } }
  "logs"   { if ($Arg) { docker compose logs -f --tail 200 $Arg } else { docker compose logs -f --tail 100 } }
  "ps"     { docker compose --profile core --profile ingest --profile observability ps }
  "seed"   { Invoke-Bash "scripts/seed/seed.sh" }
  "verify" { Invoke-Bash "scripts/verify.sh" }
  "demo"   { Invoke-Native { docker compose --profile core --profile ingest --profile observability up -d --build --wait --wait-timeout 900 }; Invoke-Bash "scripts/seed/seed.sh"; Start-Process "http://localhost" }
  "smoke"  { node scripts/smoke.mjs; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
  "perf"   { $env:MSYS_NO_PATHCONV = "1"; docker run --rm --network ipp_ipp --cpus 2 --memory 512m -v "${PWD}/scripts/perf:/perf" grafana/k6:0.57.0 run /perf/eligibility.js }
  "batch"  { node scripts/ingest/make-batch.mjs $Arg.Split(" ") }
  "chaos"  { $on = if ($Arg -eq "off") { "false" } else { "true" }; Invoke-Bash "scripts/chaos.sh $on" }
  default  { Write-Host "ipp commands: up | down | seed | verify | demo | logs [svc] | ps | reset | smoke | perf | batch [--tamper|--badsig] | chaos [on|off]" }
}
