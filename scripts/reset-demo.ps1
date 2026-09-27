[CmdletBinding()]
param(
    [string]$EnvFile = ".env.demo"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Demo environment file not found: $EnvFile"
}

$values = @{}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $pair = $line.Split("=", 2)
        $values[$pair[0].Trim()] = $pair[1].Trim()
    }
}

function Require-Value([string]$Name) {
    if ([string]::IsNullOrWhiteSpace($values[$Name])) {
        throw "Missing $Name in $EnvFile"
    }
    return $values[$Name]
}

if ((Require-Value "DEMO_MODE") -ne "true") { throw "Reset requires DEMO_MODE=true" }
if ((Require-Value "DEMO_DATA_ENABLED") -ne "true") { throw "Reset requires DEMO_DATA_ENABLED=true" }
if ((Require-Value "DEMO_DATABASE_USERNAME") -ne "clinora_demo") { throw "Reset requires DEMO_DATABASE_USERNAME=clinora_demo" }
$databaseUrl = Require-Value "DEMO_DATABASE_URL"
if ($databaseUrl -notmatch '^jdbc:postgresql://[^/]+/clinora_demo(?:\?.*)?$' -or $databaseUrl -match '(?i)prod') {
    throw "Reset refused: DEMO_DATABASE_URL must target only clinora_demo"
}

$compose = @("--env-file", $EnvFile, "-f", "docker-compose.demo.yml", "-p", "clinora-demo")
& docker compose @compose down -v
& docker compose @compose up -d --build

$port = if ($values.ContainsKey("DEMO_BACKEND_PORT")) { $values["DEMO_BACKEND_PORT"] } else { "18080" }
$deadline = (Get-Date).AddSeconds(120)
do {
    try {
        $response = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$port/actuator/health" -TimeoutSec 3
        if ($response.StatusCode -eq 200) {
            Write-Host "Demo reset complete: backend healthy on port $port"
            exit 0
        }
    } catch { }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

throw "Demo backend did not become healthy within 120 seconds"
