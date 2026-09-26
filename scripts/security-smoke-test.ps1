[CmdletBinding()]
param(
    [switch]$SkipAutomatedTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:CriticalFailures = 0
$script:Warnings = 0
$script:SkippedChecks = 0

function Write-Section {
    param([Parameter(Mandatory = $true)][string]$Title)

    Write-Host ""
    Write-Host "[$Title" -ForegroundColor Cyan
}

function Write-Check {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][ValidateSet("PASS", "FAIL", "INFO", "SKIP")][string]$Status,
        [string]$Detail = "",
        [switch]$Critical,
        [switch]$Warning
    )

    $color = switch ($Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        "INFO" { "Yellow" }
        "SKIP" { "DarkYellow" }
    }

    $suffix = if ([string]::IsNullOrWhiteSpace($Detail)) { "" } else { " - $Detail" }
    Write-Host ("{0,-32} {1}{2}" -f $Name, $Status, $suffix) -ForegroundColor $color

    if ($Status -eq "FAIL") {
        if ($Critical) {
            $script:CriticalFailures++
        }
        else {
            $script:Warnings++
        }
    }

    if ($Status -eq "SKIP") {
        $script:SkippedChecks++
    }

    if ($Warning) {
        $script:Warnings++
    }
}

function Copy-ResponseHeaders {
    param($Headers)

    $copy = @{}
    if ($null -eq $Headers) {
        return $copy
    }

    $headerKeys = @()
    if ($Headers.PSObject.Properties.Name -contains "AllKeys") {
        $headerKeys = @($Headers.AllKeys)
    }
    elseif ($Headers.PSObject.Properties.Name -contains "Keys") {
        $headerKeys = @($Headers.Keys)
    }

    foreach ($key in $headerKeys) {
        if ($null -ne $key) {
            $copy[[string]$key] = [string]$Headers[$key]
        }
    }

    return $copy
}

function Get-HeaderValue {
    param(
        [hashtable]$Headers,
        [Parameter(Mandatory = $true)][string]$Name
    )

    foreach ($key in $Headers.Keys) {
        if ($key -ieq $Name) {
            return [string]$Headers[$key]
        }
    }

    return $null
}

function Convert-ResponseBodyToString {
    param($Content)

    if ($Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Content)
    }

    return [string]$Content
}

function Invoke-LocalRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [ValidateSet("GET", "POST", "OPTIONS", "TRACE")][string]$Method = "GET",
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )

    $parameters = @{
        Uri = $Uri
        Method = $Method
        Headers = $Headers
        TimeoutSec = 8
        UseBasicParsing = $true
        ErrorAction = "Stop"
    }

    if (-not [string]::IsNullOrEmpty($Body)) {
        $parameters["Body"] = $Body
        $parameters["ContentType"] = "application/json"
    }

    try {
        $response = Invoke-WebRequest @parameters
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Headers = Copy-ResponseHeaders $response.Headers
            Body = Convert-ResponseBodyToString $response.Content
            Error = $null
        }
    }
    catch {
        $webResponse = $null
        if ($null -ne $_.Exception -and $_.Exception.PSObject.Properties.Name -contains "Response") {
            $webResponse = $_.Exception.Response
        }
        if ($null -eq $webResponse) {
            return [pscustomobject]@{
                StatusCode = $null
                Headers = @{}
                Body = ""
                Error = $_.Exception.Message
            }
        }

        $body = ""
        try {
            $stream = $webResponse.GetResponseStream()
            if ($null -ne $stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                try {
                    $body = $reader.ReadToEnd()
                }
                finally {
                    $reader.Dispose()
                }
            }
        }
        catch {
            $body = ""
        }

        return [pscustomobject]@{
            StatusCode = [int]$webResponse.StatusCode
            Headers = Copy-ResponseHeaders $webResponse.Headers
            Body = $body
            Error = $null
        }
    }
}

function Get-ComposeMetadata {
    param([Parameter(Mandatory = $true)][string]$ComposePath)

    $metadata = @{
        BackendContainer = "patient-management-backend"
        PostgresContainer = "patient-management-postgres"
        BackendHostPort = $null
        BackendContainerPort = $null
        BackendBindAddress = $null
        PostgresHostPort = $null
        PostgresContainerPort = $null
        PostgresBindAddress = $null
    }

    $currentService = $null
    foreach ($line in Get-Content -LiteralPath $ComposePath) {
        if ($line -match '^  (?<service>[A-Za-z0-9_-]+):\s*$') {
            $currentService = $matches.service
            continue
        }

        if ($line -match '^container_name:\s*(?<name>\S+)') {
            continue
        }

        if ($line -match '^    container_name:\s*(?<name>\S+)') {
            if ($currentService -eq "backend") {
                $metadata.BackendContainer = $matches.name.Trim('"', "'")
            }
            elseif ($currentService -eq "postgres") {
                $metadata.PostgresContainer = $matches.name.Trim('"', "'")
            }
            continue
        }

        if ($currentService -notin @("backend", "postgres")) {
            continue
        }

        if ($line -match '^\s*-\s*["'']?(?:(?<bind>\[[^\]]+\]|[^:\s]+):)?(?<host>\d+):(?<container>\d+)["'']?\s*$') {
            if ($currentService -eq "backend") {
                $metadata.BackendHostPort = [int]$matches.host
                $metadata.BackendContainerPort = [int]$matches.container
                $metadata.BackendBindAddress = $matches.bind
            }
            else {
                $metadata.PostgresHostPort = [int]$matches.host
                $metadata.PostgresContainerPort = [int]$matches.container
                $metadata.PostgresBindAddress = $matches.bind
            }
        }
    }

    return [pscustomobject]$metadata
}

function Get-ConfiguredFrontendPort {
    param([Parameter(Mandatory = $true)][string]$ViteConfigPath)

    $source = Get-Content -LiteralPath $ViteConfigPath -Raw
    $match = [regex]::Match($source, 'server:\s*\{[\s\S]*?port:\s*(?<port>\d+)')
    if ($match.Success) {
        return [int]$match.Groups["port"].Value
    }

    return $null
}

function Get-ConfiguredCorsOrigin {
    param([Parameter(Mandatory = $true)][string]$ApplicationConfigPath)

    $source = Get-Content -LiteralPath $ApplicationConfigPath -Raw
    $match = [regex]::Match($source, 'allowed-origins:\s*\$\{CORS_ALLOWED_ORIGINS:(?<origin>[^}]+)\}')
    if ($match.Success) {
        return $match.Groups["origin"].Value.Trim()
    }

    return $null
}

function Get-SurefireSummary {
    param([Parameter(Mandatory = $true)][string]$ReportDirectory)

    if (-not (Test-Path -LiteralPath $ReportDirectory)) {
        return $null
    }

    $reports = @(Get-ChildItem -LiteralPath $ReportDirectory -Filter "TEST-*.xml" -ErrorAction SilentlyContinue)
    if ($reports.Count -eq 0) {
        return $null
    }

    $summary = [ordered]@{ Tests = 0; Failures = 0; Errors = 0; Skipped = 0 }
    foreach ($report in $reports) {
        [xml]$xml = Get-Content -LiteralPath $report.FullName
        $suite = $xml.testsuite
        $summary.Tests += [int]$suite.tests
        $summary.Failures += [int]$suite.failures
        $summary.Errors += [int]$suite.errors
        $summary.Skipped += [int]$suite.skipped
    }

    return [pscustomobject]$summary
}

function Invoke-ExternalCheck {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [int]$TimeoutSeconds = 300
    )

    $commandInfo = Get-Command $Command -ErrorAction SilentlyContinue
    if ($null -eq $commandInfo) {
        return [pscustomobject]@{ Available = $false; TimedOut = $false; ExitCode = $null }
    }

    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $commandInfo.Source
    $processInfo.Arguments = (($Arguments | ForEach-Object { '"{0}"' -f ($_ -replace '"', '\"') }) -join " ")
    $processInfo.WorkingDirectory = $WorkingDirectory
    $processInfo.UseShellExecute = $false
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $processInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $processInfo
    [void]$process.Start()
    $standardOutTask = $process.StandardOutput.ReadToEndAsync()
    $standardErrorTask = $process.StandardError.ReadToEndAsync()

    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        $process.Kill()
        $process.WaitForExit()
        return [pscustomobject]@{ Available = $true; TimedOut = $true; ExitCode = $null }
    }

    [void]$standardOutTask.GetAwaiter().GetResult()
    [void]$standardErrorTask.GetAwaiter().GetResult()
    return [pscustomobject]@{ Available = $true; TimedOut = $false; ExitCode = [int]$process.ExitCode }
}

function Find-SuspiciousRepositorySecrets {
    param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

    $files = @(
        (Join-Path -Path $RepositoryRoot -ChildPath ".env.example"),
        (Join-Path -Path $RepositoryRoot -ChildPath "backend/.env.example"),
        (Join-Path -Path $RepositoryRoot -ChildPath "docker-compose.yml"),
        (Join-Path -Path $RepositoryRoot -ChildPath "README.md")
    ) | Where-Object { Test-Path -LiteralPath $_ }

    $directories = @(
        (Join-Path -Path $RepositoryRoot -ChildPath "backend/src/main"),
        (Join-Path -Path $RepositoryRoot -ChildPath "frontend/src"),
        (Join-Path -Path $RepositoryRoot -ChildPath "scripts")
    ) | Where-Object { Test-Path -LiteralPath $_ }

    foreach ($directory in $directories) {
        $files += Get-ChildItem -LiteralPath $directory -File -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\(node_modules|dist|target|\.git)\\' } |
            Select-Object -ExpandProperty FullName
    }

    $patterns = @(
        '(?i)\bsk-[A-Za-z0-9_-]{16,}',
        '(?i)\bAIza[A-Za-z0-9_-]{20,}',
        '(?i)\b(?:rk_live|sk_live)_[A-Za-z0-9_-]{16,}',
        '(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
        '(?i)\b(?:openai|openrouter|twilio|msg91)[A-Za-z0-9_-]*(?:key|token|secret)?\s*[:=]\s*["''][A-Za-z0-9_-]{16,}'
    )

    $findings = New-Object System.Collections.Generic.HashSet[string]
    foreach ($file in $files | Select-Object -Unique) {
        foreach ($line in Get-Content -LiteralPath $file -ErrorAction SilentlyContinue) {
            if ($line -match '(?i)placeholder|replace_with|your_[a-z_]+|<[^>]+>|example|\$\{') {
                continue
            }

            foreach ($pattern in $patterns) {
                if ($line -match $pattern) {
                    [void]$findings.Add($file)
                    break
                }
            }
        }
    }

    return @($findings)
}

function Find-SuspiciousContainerLogData {
    param([Parameter(Mandatory = $true)][string]$ContainerName)

    $logs = & docker logs --tail 300 $ContainerName 2>&1
    if ($LASTEXITCODE -ne 0) {
        return $null
    }

    $patterns = @(
        '(?i)authorization:\s*bearer\s+\S+',
        '(?i)bearer\s+[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}',
        '(?i)\b(?:sk-|AIza)[A-Za-z0-9_-]{16,}',
        '(?i)(?:refresh|feedback|prescription)[-_ ]?token\s*[=:]\s*[A-Za-z0-9_-]{24,}'
    )

    foreach ($pattern in $patterns) {
        if ($logs -match $pattern) {
            return $true
        }
    }

    return $false
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composePath = Join-Path $repositoryRoot "docker-compose.yml"
$backendDirectory = Join-Path $repositoryRoot "backend"
$frontendDirectory = Join-Path $repositoryRoot "frontend"
$applicationConfigPath = Join-Path $backendDirectory "src/main/resources/application.yml"
$securityConfigPath = Join-Path $backendDirectory "src/main/java/com/patientmanagement/auth/security/SecurityConfig.java"
$viteConfigPath = Join-Path $frontendDirectory "vite.config.ts"

if (-not (Test-Path -LiteralPath $composePath) -or -not (Test-Path -LiteralPath $applicationConfigPath)) {
    Write-Host "This script must be run from the Clinora repository scripts directory." -ForegroundColor Red
    exit 2
}

$compose = Get-ComposeMetadata -ComposePath $composePath
$backendPort = if ($null -ne $compose.BackendHostPort) { $compose.BackendHostPort } else { 8080 }
$frontendPort = Get-ConfiguredFrontendPort -ViteConfigPath $viteConfigPath
if ($null -eq $frontendPort) {
    $frontendPort = 5173
}
$corsOrigin = Get-ConfiguredCorsOrigin -ApplicationConfigPath $applicationConfigPath
$backendUrl = "http://localhost:$backendPort"
$frontendUrl = "http://localhost:$frontendPort"
$healthPath = "/api/v1/health"
$actuatorHealthPath = "/actuator/health"
$protectedPath = "/api/v1/patients"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "CLINORA SECURITY SMOKE TEST" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "Repository: $repositoryRoot"
Write-Host "Backend:    $backendUrl"
Write-Host "Frontend:   $frontendUrl"

$dockerAvailable = $null -ne (Get-Command docker -ErrorAction SilentlyContinue)

Write-Section "1] Docker and Runtime"
if (-not $dockerAvailable) {
    Write-Check -Name "Docker availability" -Status "SKIP" -Detail "Docker CLI is unavailable" -Warning
}
else {
    $dockerInfo = & docker info --format "{{.ServerVersion}}" 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($dockerInfo)) {
        Write-Check -Name "Docker availability" -Status "SKIP" -Detail "Docker daemon is unavailable" -Warning
        $dockerAvailable = $false
    }
    else {
        Write-Check -Name "Docker availability" -Status "PASS" -Detail "daemon reachable"

        foreach ($container in @($compose.BackendContainer, $compose.PostgresContainer)) {
            $status = & docker inspect --format "{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}" $container 2>$null
            if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($status)) {
                Write-Check -Name "Container $container" -Status "FAIL" -Detail "not found or not running" -Critical
                continue
            }

            $state, $health = $status -split '\|', 2
            if ($state -ne "running") {
                Write-Check -Name "Container $container" -Status "FAIL" -Detail "state: $state" -Critical
            }
            elseif ($health -eq "healthy") {
                Write-Check -Name "Container $container" -Status "PASS" -Detail "running and healthy"
            }
            elseif ($health -eq "none") {
                Write-Check -Name "Container $container" -Status "INFO" -Detail "running; no Docker health check" -Warning
            }
            else {
                Write-Check -Name "Container $container" -Status "FAIL" -Detail "health: $health" -Critical
            }
        }
    }
}

Write-Section "2] Network and Ports"
foreach ($binding in @(
    [pscustomobject]@{ Name = "Backend port binding"; Container = $compose.BackendContainer; Port = $compose.BackendContainerPort; Expected = $compose.BackendBindAddress },
    [pscustomobject]@{ Name = "PostgreSQL port binding"; Container = $compose.PostgresContainer; Port = $compose.PostgresContainerPort; Expected = $compose.PostgresBindAddress }
)) {
    if (-not $dockerAvailable) {
        Write-Check -Name $binding.Name -Status "SKIP" -Detail "Docker runtime unavailable" -Warning
        continue
    }

    if ([string]::IsNullOrWhiteSpace($binding.Expected) -or $binding.Expected -notin @("127.0.0.1", "::1", "[::1]")) {
        Write-Check -Name "$($binding.Name) config" -Status "FAIL" -Detail "Compose does not declare a loopback binding" -Critical
    }
    else {
        Write-Check -Name "$($binding.Name) config" -Status "PASS" -Detail "Compose expects $($binding.Expected)"
    }

    if ($null -eq $binding.Port) {
        Write-Check -Name $binding.Name -Status "INFO" -Detail "container port could not be read from Compose" -Warning
        continue
    }

    $runtimeBinding = & docker port $binding.Container "$($binding.Port)/tcp" 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($runtimeBinding)) {
        Write-Check -Name $binding.Name -Status "FAIL" -Detail "no published runtime binding found" -Critical
    }
    elseif ($runtimeBinding | Where-Object { $_ -match '^(127\.0\.0\.1|\[::1\]|::1):' }) {
        Write-Check -Name $binding.Name -Status "PASS" -Detail "loopback-only runtime binding"
    }
    else {
        Write-Check -Name $binding.Name -Status "FAIL" -Detail "runtime binding is not loopback-only" -Critical
    }
}

Write-Section "3] Backend Health"
$applicationHealth = Invoke-LocalRequest -Uri "$backendUrl$healthPath"
if ($applicationHealth.StatusCode -eq 200 -and $applicationHealth.Body -match '"status"\s*:\s*"UP"') {
    Write-Check -Name "Application health" -Status "PASS" -Detail "HTTP 200 / UP"
}
elseif ($null -eq $applicationHealth.StatusCode) {
    Write-Check -Name "Application health" -Status "FAIL" -Detail "backend did not respond" -Critical
}
else {
    Write-Check -Name "Application health" -Status "FAIL" -Detail "HTTP $($applicationHealth.StatusCode)" -Critical
}

$actuatorHealth = Invoke-LocalRequest -Uri "$backendUrl$actuatorHealthPath"
if ($actuatorHealth.StatusCode -eq 200 -and $actuatorHealth.Body -match '"status"\s*:\s*"UP"') {
    Write-Check -Name "Actuator health" -Status "PASS" -Detail "HTTP 200 / UP"
}
elseif ($null -eq $actuatorHealth.StatusCode) {
    Write-Check -Name "Actuator health" -Status "FAIL" -Detail "backend did not respond" -Critical
}
else {
    Write-Check -Name "Actuator health" -Status "FAIL" -Detail "HTTP $($actuatorHealth.StatusCode)" -Critical
}

Write-Section "4] Authentication"
$unauthenticated = Invoke-LocalRequest -Uri "$backendUrl$protectedPath"
if ($unauthenticated.StatusCode -eq 401) {
    Write-Check -Name "Protected API without JWT" -Status "PASS" -Detail "HTTP 401"
}
else {
    Write-Check -Name "Protected API without JWT" -Status "FAIL" -Detail "expected 401, got HTTP $($unauthenticated.StatusCode)" -Critical
}

$malformedToken = Invoke-LocalRequest -Uri "$backendUrl$protectedPath" -Headers @{ Authorization = "Bearer malformed-token" }
if ($malformedToken.StatusCode -eq 401) {
    Write-Check -Name "Malformed JWT rejection" -Status "PASS" -Detail "HTTP 401"
}
else {
    Write-Check -Name "Malformed JWT rejection" -Status "FAIL" -Detail "expected 401, got HTTP $($malformedToken.StatusCode)" -Critical
}

$smokeEmail = [Environment]::GetEnvironmentVariable("SMOKE_TEST_EMAIL")
$smokePassword = [Environment]::GetEnvironmentVariable("SMOKE_TEST_PASSWORD")
if ([string]::IsNullOrWhiteSpace($smokeEmail) -or [string]::IsNullOrWhiteSpace($smokePassword)) {
    Write-Check -Name "Authenticated live check" -Status "SKIP" -Detail "SMOKE_TEST_EMAIL and SMOKE_TEST_PASSWORD were not both supplied"
}
else {
    $accessToken = $null
    try {
        $loginPayload = @{ username = $smokeEmail; password = $smokePassword } | ConvertTo-Json -Compress
        $login = Invoke-LocalRequest -Uri "$backendUrl/api/v1/auth/login" -Method "POST" -Body $loginPayload
        if ($login.StatusCode -ne 200) {
            Write-Check -Name "Authenticated live check" -Status "FAIL" -Detail "login returned HTTP $($login.StatusCode)" -Critical
        }
        else {
            $accessToken = ($login.Body | ConvertFrom-Json).accessToken
            if ([string]::IsNullOrWhiteSpace($accessToken)) {
                Write-Check -Name "Authenticated live check" -Status "FAIL" -Detail "login response omitted an access token" -Critical
            }
            else {
                $authenticated = Invoke-LocalRequest -Uri "$backendUrl$protectedPath" -Headers @{ Authorization = "Bearer $accessToken" }
                if ($authenticated.StatusCode -eq 200) {
                    Write-Check -Name "Authenticated live check" -Status "PASS" -Detail "protected read succeeded"
                }
                else {
                    Write-Check -Name "Authenticated live check" -Status "FAIL" -Detail "protected read returned HTTP $($authenticated.StatusCode)" -Critical
                }
            }
        }
    }
    finally {
        $accessToken = $null
    }
}
Write-Check -Name "Cross-doctor live check" -Status "SKIP" -Detail "requires two pre-existing safe doctor accounts and scoped records"

Write-Section "5] Security Headers"
if ($applicationHealth.StatusCode -ne 200) {
    Write-Check -Name "Security headers" -Status "SKIP" -Detail "backend health endpoint is unavailable" -Warning
}
else {
    $securitySource = Get-Content -LiteralPath $securityConfigPath -Raw
    $expectedHeaders = @()
    if ($securitySource -match 'contentTypeOptions') {
        $expectedHeaders += [pscustomobject]@{ Name = "X-Content-Type-Options"; Expected = "nosniff" }
    }
    if ($securitySource -match 'frameOptions\([^\)]*deny\(\)') {
        $expectedHeaders += [pscustomobject]@{ Name = "X-Frame-Options"; Expected = "DENY" }
    }
    if ($securitySource -match 'ReferrerPolicy\.NO_REFERRER') {
        $expectedHeaders += [pscustomobject]@{ Name = "Referrer-Policy"; Expected = "no-referrer" }
    }
    $permissionsMatch = [regex]::Match($securitySource, '"Permissions-Policy",\s*"(?<value>[^"]+)"')
    if ($permissionsMatch.Success) {
        $expectedHeaders += [pscustomobject]@{ Name = "Permissions-Policy"; Expected = $permissionsMatch.Groups["value"].Value }
    }

    foreach ($header in $expectedHeaders) {
        $actual = Get-HeaderValue -Headers $applicationHealth.Headers -Name $header.Name
        if ($actual -eq $header.Expected) {
            Write-Check -Name $header.Name -Status "PASS"
        }
        else {
            Write-Check -Name $header.Name -Status "FAIL" -Detail "missing or unexpected value" -Critical
        }
    }

    $cacheControl = Get-HeaderValue -Headers $applicationHealth.Headers -Name "Cache-Control"
    if ($cacheControl -match '(?i)no-store') {
        Write-Check -Name "Cache-Control no-store" -Status "PASS"
    }
    else {
        Write-Check -Name "Cache-Control no-store" -Status "FAIL" -Detail "missing from backend response" -Critical
    }

    if ($backendUrl -match '^https://') {
        $hsts = Get-HeaderValue -Headers $applicationHealth.Headers -Name "Strict-Transport-Security"
        if ([string]::IsNullOrWhiteSpace($hsts)) {
            Write-Check -Name "HSTS" -Status "FAIL" -Detail "HTTPS response omitted HSTS" -Critical
        }
        else {
            Write-Check -Name "HSTS" -Status "PASS"
        }
    }
    else {
        Write-Check -Name "HSTS" -Status "INFO" -Detail "HTTPS is not active in this local runtime"
    }
}

$trace = Invoke-LocalRequest -Uri "$backendUrl$healthPath" -Method "TRACE"
if ($trace.StatusCode -in @(400, 401, 403, 405)) {
    Write-Check -Name "TRACE method" -Status "PASS" -Detail "HTTP $($trace.StatusCode)"
}
elseif ($trace.StatusCode -ge 200 -and $trace.StatusCode -lt 300) {
    Write-Check -Name "TRACE method" -Status "FAIL" -Detail "TRACE succeeded" -Critical
}
elseif ($null -eq $trace.StatusCode) {
    Write-Check -Name "TRACE method" -Status "INFO" -Detail "request could not be sent" -Warning
}
else {
    Write-Check -Name "TRACE method" -Status "FAIL" -Detail "unexpected HTTP $($trace.StatusCode)" -Critical
}

Write-Section "6] Actuator"
foreach ($endpoint in @("env", "configprops", "beans", "mappings", "loggers")) {
    $response = Invoke-LocalRequest -Uri "$backendUrl/actuator/$endpoint"
    if ($response.StatusCode -eq 200) {
        Write-Check -Name "Actuator /$endpoint" -Status "FAIL" -Detail "publicly accessible" -Critical
    }
    elseif ($response.StatusCode -in @(401, 403, 404)) {
        Write-Check -Name "Actuator /$endpoint" -Status "PASS" -Detail "HTTP $($response.StatusCode)"
    }
    elseif ($null -eq $response.StatusCode) {
        Write-Check -Name "Actuator /$endpoint" -Status "INFO" -Detail "backend did not respond" -Warning
    }
    else {
        Write-Check -Name "Actuator /$endpoint" -Status "FAIL" -Detail "unexpected HTTP $($response.StatusCode)" -Critical
    }
}

Write-Section "7] CORS"
if ([string]::IsNullOrWhiteSpace($corsOrigin)) {
    Write-Check -Name "Configured CORS origin" -Status "INFO" -Detail "could not be read from application.yml" -Warning
}
else {
    $allowedCors = Invoke-LocalRequest -Uri "$backendUrl$protectedPath" -Method "OPTIONS" -Headers @{
        Origin = $corsOrigin
        "Access-Control-Request-Method" = "GET"
        "Access-Control-Request-Headers" = "Authorization"
    }
    $allowedOrigin = Get-HeaderValue -Headers $allowedCors.Headers -Name "Access-Control-Allow-Origin"
    $allowCredentials = Get-HeaderValue -Headers $allowedCors.Headers -Name "Access-Control-Allow-Credentials"
    if ($allowedCors.StatusCode -in @(200, 204) -and $allowedOrigin -eq $corsOrigin -and $allowCredentials -ne "true") {
        Write-Check -Name "Configured CORS origin" -Status "PASS"
    }
    else {
        Write-Check -Name "Configured CORS origin" -Status "FAIL" -Detail "configured origin was not safely accepted" -Critical
    }

    $rejectedCors = Invoke-LocalRequest -Uri "$backendUrl$protectedPath" -Method "OPTIONS" -Headers @{
        Origin = "https://evil.example"
        "Access-Control-Request-Method" = "GET"
        "Access-Control-Request-Headers" = "Authorization"
    }
    $rejectedOrigin = Get-HeaderValue -Headers $rejectedCors.Headers -Name "Access-Control-Allow-Origin"
    if ($rejectedOrigin -ne "*" -and $rejectedOrigin -ne "https://evil.example") {
        Write-Check -Name "Arbitrary CORS origin" -Status "PASS"
    }
    else {
        Write-Check -Name "Arbitrary CORS origin" -Status "FAIL" -Detail "arbitrary origin was accepted" -Critical
    }
}

Write-Section "8] Public Token Security"
foreach ($publicEndpoint in @(
    "/api/v1/prescription-access/security-smoke-invalid-token/pdf",
    "/api/v1/feedback-access/security-smoke-invalid-token"
)) {
    $response = Invoke-LocalRequest -Uri "$backendUrl$publicEndpoint"
    if ($response.StatusCode -eq 404) {
        Write-Check -Name "Invalid public token" -Status "PASS" -Detail "safe HTTP 404"
    }
    elseif ($null -eq $response.StatusCode) {
        Write-Check -Name "Invalid public token" -Status "SKIP" -Detail "backend is unavailable" -Warning
    }
    else {
        Write-Check -Name "Invalid public token" -Status "FAIL" -Detail "expected 404, got HTTP $($response.StatusCode)" -Critical
    }
}
Write-Check -Name "Public token valid flow" -Status "SKIP" -Detail "no valid token is read or created by this tool"

Write-Section "9] Frontend and Swagger"
$frontend = Invoke-LocalRequest -Uri $frontendUrl
if ($frontend.StatusCode -eq 200) {
    Write-Check -Name "Frontend availability" -Status "PASS" -Detail "HTTP 200"
}
elseif ($null -eq $frontend.StatusCode) {
    Write-Check -Name "Frontend availability" -Status "SKIP" -Detail "development server is not running" -Warning
}
else {
    Write-Check -Name "Frontend availability" -Status "FAIL" -Detail "HTTP $($frontend.StatusCode)" -Critical
}

$swagger = Invoke-LocalRequest -Uri "$backendUrl/swagger-ui.html"
if ($swagger.StatusCode -eq 200 -or $swagger.StatusCode -in @(301, 302)) {
    Write-Check -Name "Swagger/OpenAPI" -Status "INFO" -Detail "available for local development"
}
elseif ($null -eq $swagger.StatusCode) {
    Write-Check -Name "Swagger/OpenAPI" -Status "INFO" -Detail "backend is unavailable"
}
else {
    Write-Check -Name "Swagger/OpenAPI" -Status "INFO" -Detail "HTTP $($swagger.StatusCode)"
}

Write-Section "10] Secret and Log Scan"
$secretFindings = @(Find-SuspiciousRepositorySecrets -RepositoryRoot $repositoryRoot)
if ($secretFindings.Count -eq 0) {
    Write-Check -Name "Repository secret scan" -Status "PASS" -Detail "no real-looking credential patterns found"
}
else {
    $names = ($secretFindings | ForEach-Object { $_.Substring($repositoryRoot.Length).TrimStart('\\') }) -join ", "
    Write-Check -Name "Repository secret scan" -Status "FAIL" -Detail "suspicious pattern in $names" -Critical
}

if (-not $dockerAvailable) {
    Write-Check -Name "Backend log secret scan" -Status "SKIP" -Detail "Docker runtime unavailable" -Warning
}
else {
    $logScan = Find-SuspiciousContainerLogData -ContainerName $compose.BackendContainer
    if ($null -eq $logScan) {
        Write-Check -Name "Backend log secret scan" -Status "SKIP" -Detail "backend logs were unavailable" -Warning
    }
    elseif ($logScan) {
        Write-Check -Name "Backend log secret scan" -Status "FAIL" -Detail "suspicious secret-like data detected; value withheld" -Critical
    }
    else {
        Write-Check -Name "Backend log secret scan" -Status "PASS" -Detail "recent logs contain no obvious secret patterns"
    }
}

Write-Section "11] Automated Tests"
if ($SkipAutomatedTests) {
    Write-Check -Name "Backend test suite" -Status "SKIP" -Detail "-SkipAutomatedTests was supplied"
    Write-Check -Name "Frontend test suite" -Status "SKIP" -Detail "-SkipAutomatedTests was supplied"
    Write-Check -Name "Frontend production build" -Status "SKIP" -Detail "-SkipAutomatedTests was supplied"
}
else {
    $backendTests = Invoke-ExternalCheck -Command "mvn.cmd" -Arguments @("-q", "test") -WorkingDirectory $backendDirectory -TimeoutSeconds 600
    if (-not $backendTests.Available) {
        Write-Check -Name "Backend test suite" -Status "SKIP" -Detail "mvn.cmd is unavailable" -Warning
    }
    elseif ($backendTests.TimedOut) {
        Write-Check -Name "Backend test suite" -Status "FAIL" -Detail "timed out" -Critical
    }
    elseif ($backendTests.ExitCode -ne 0) {
        Write-Check -Name "Backend test suite" -Status "FAIL" -Detail "Maven exited $($backendTests.ExitCode)" -Critical
    }
    else {
        $summary = Get-SurefireSummary -ReportDirectory (Join-Path $backendDirectory "target/surefire-reports")
        if ($null -eq $summary) {
            Write-Check -Name "Backend test suite" -Status "PASS" -Detail "Maven completed successfully"
        }
        else {
            Write-Check -Name "Backend test suite" -Status "PASS" -Detail "$($summary.Tests) tests, $($summary.Failures) failures, $($summary.Errors) errors, $($summary.Skipped) skipped"
        }
    }

    $frontendTests = Invoke-ExternalCheck -Command "npm.cmd" -Arguments @("run", "test") -WorkingDirectory $frontendDirectory -TimeoutSeconds 300
    if (-not $frontendTests.Available) {
        Write-Check -Name "Frontend test suite" -Status "SKIP" -Detail "npm.cmd is unavailable" -Warning
    }
    elseif ($frontendTests.TimedOut) {
        Write-Check -Name "Frontend test suite" -Status "FAIL" -Detail "timed out" -Critical
    }
    elseif ($frontendTests.ExitCode -eq 0) {
        Write-Check -Name "Frontend test suite" -Status "PASS" -Detail "npm test completed successfully"
    }
    else {
        Write-Check -Name "Frontend test suite" -Status "FAIL" -Detail "npm exited $($frontendTests.ExitCode)" -Critical
    }

    $frontendBuild = Invoke-ExternalCheck -Command "npm.cmd" -Arguments @("run", "build") -WorkingDirectory $frontendDirectory -TimeoutSeconds 300
    if (-not $frontendBuild.Available) {
        Write-Check -Name "Frontend production build" -Status "SKIP" -Detail "npm.cmd is unavailable" -Warning
    }
    elseif ($frontendBuild.TimedOut) {
        Write-Check -Name "Frontend production build" -Status "FAIL" -Detail "timed out" -Critical
    }
    elseif ($frontendBuild.ExitCode -eq 0) {
        Write-Check -Name "Frontend production build" -Status "PASS"
    }
    else {
        Write-Check -Name "Frontend production build" -Status "FAIL" -Detail "npm exited $($frontendBuild.ExitCode)" -Critical
    }
}

Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "FINAL SUMMARY" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "Critical failures: $script:CriticalFailures"
Write-Host "Warnings: $script:Warnings"
Write-Host "Skipped checks: $script:SkippedChecks"

if ($script:CriticalFailures -gt 0) {
    Write-Host "OVERALL RESULT: FAIL" -ForegroundColor Red
    exit 1
}

if ($script:Warnings -gt 0 -or $script:SkippedChecks -gt 0) {
    Write-Host "OVERALL RESULT: PASS WITH WARNINGS" -ForegroundColor Yellow
    exit 0
}

Write-Host "OVERALL RESULT: PASS" -ForegroundColor Green
exit 0
