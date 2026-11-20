<#
  IPP command runner for Windows PowerShell. Same commands as the Makefile.
  Usage: ./ipp.ps1 <up|down|seed|verify|demo|logs|reset|ps> [service]
#>
param([Parameter(Position = 0)][string]$Cmd = "help", [Parameter(Position = 1)][string]$Arg = "")
$ErrorActionPreference = "Stop"
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

switch ($Cmd) {
  "up"     { docker compose --profile core up -d --build --wait --wait-timeout 600 }
  "down"   { docker compose --profile core --profile ingest --profile observability down }
  "reset"  { docker compose --profile core --profile ingest --profile observability down -v --remove-orphans }
  "logs"   { if ($Arg) { docker compose logs -f --tail 200 $Arg } else { docker compose logs -f --tail 100 } }
  "ps"     { docker compose ps }
  "seed"   { Invoke-Bash "scripts/seed/seed.sh" }
  "verify" { Invoke-Bash "scripts/verify.sh" }
  "demo"   { docker compose --profile core --profile ingest up -d --build --wait --wait-timeout 900; Invoke-Bash "scripts/seed/seed.sh"; Start-Process "http://localhost" }
  default  { Write-Host "ipp commands: up | down | seed | verify | demo | logs [svc] | ps | reset" }
}
