[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$Restart
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $root "target\run"
$pidFile = Join-Path $runDirectory "housekeeping-pids.json"

$services = @(
    @{
        Name = "config-server"
        Module = "housekeeping-config-server"
        Port = 8888
        ReadyUrl = $null
    },
    @{
        Name = "discovery-server"
        Module = "housekeeping-discovery-server"
        Port = 8761
        ReadyUrl = "http://localhost:8761/"
    },
    @{
        Name = "customers-service"
        Module = "housekeeping-customers-service"
        Port = 8181
        ReadyUrl = "http://localhost:8181/actuator/health"
    },
    @{
        Name = "vets-service"
        Module = "housekeeping-vets-service"
        Port = 8083
        ReadyUrl = "http://localhost:8083/actuator/health"
    },
    @{
        Name = "visits-service"
        Module = "housekeeping-visits-service"
        Port = 8082
        ReadyUrl = "http://localhost:8082/actuator/health"
    },
    @{
        Name = "genai-service"
        Module = "housekeeping-genai-service"
        Port = 8084
        ReadyUrl = $null
    },
    @{
        Name = "api-gateway"
        Module = "housekeeping-api-gateway"
        Port = 8080
        ReadyUrl = "http://localhost:8080/"
    }
)

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-Port([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $result = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $result.AsyncWaitHandle.WaitOne(500)) {
            return $false
        }
        $client.EndConnect($result)
        return $true
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Stop-TrackedProcesses {
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return
    }

    $tracked = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
    foreach ($entry in $tracked) {
        $process = Get-Process -Id $entry.Pid -ErrorAction SilentlyContinue
        if ($process) {
            Write-Host "Stopping $($entry.Name) (PID $($entry.Pid))..."
            Stop-Process -Id $entry.Pid -Force
        }
    }

    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

function Get-ServiceJar([string]$Module) {
    $target = Join-Path $root "$Module\target"
    $jar = Get-ChildItem -LiteralPath $target -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*.original" -and $_.Name -notlike "*-sources.jar" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $jar) {
        throw "No runnable JAR found for $Module. Run the script without -SkipBuild."
    }

    return $jar.FullName
}

function Wait-Service {
    param(
        [hashtable]$Service,
        [System.Diagnostics.Process]$Process,
        [string]$LogFile,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) {
            Write-Host ""
            Write-Host "$($Service.Name) exited unexpectedly. Last log lines:" -ForegroundColor Red
            Get-Content -LiteralPath $LogFile -Tail 30 -ErrorAction SilentlyContinue
            throw "$($Service.Name) failed to start."
        }

        if ($Service.ReadyUrl) {
            try {
                $response = Invoke-WebRequest -Uri $Service.ReadyUrl -UseBasicParsing -TimeoutSec 3
                if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                    return
                }
            }
            catch {
                # Service is still starting.
            }
        }
        elseif (Test-Port $Service.Port) {
            return
        }

        Start-Sleep -Seconds 2
    }

    Get-Content -LiteralPath $LogFile -Tail 30 -ErrorAction SilentlyContinue
    throw "Timed out waiting for $($Service.Name) on port $($Service.Port)."
}

Set-Location $root
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null

function New-RandomSecret {
    $bytes = [byte[]]::new(48)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

# Child services share per-run random keys. They are never written to logs or source files.
$env:HOUSEKEEPING_JWT_SECRET = New-RandomSecret
$env:HOUSEKEEPING_INTERNAL_JWT_SECRET = New-RandomSecret

if ($Restart) {
    Write-Step "Stopping services started by this script"
    Stop-TrackedProcesses
}

$occupied = @()
foreach ($service in $services) {
    if (Test-Port $service.Port) {
        $occupied += "$($service.Name):$($service.Port)"
    }
}
if ($occupied.Count -gt 0) {
    throw "Required ports are already occupied: $($occupied -join ', '). Use -Restart for processes previously started by this script."
}

if (-not $SkipBuild) {
    Write-Step "Building all application JARs"
    & "$root\mvnw.cmd" -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed."
    }
}

if (-not $env:DEEPSEEK_API_KEY) {
    Write-Warning "DEEPSEEK_API_KEY is not set. The platform will start, but AI chat will be unavailable."
}
if (-not $env:DASHSCOPE_API_KEY) {
    Write-Warning "DASHSCOPE_API_KEY is not set. Embedding-related AI features may be unavailable."
}

$started = @()
try {
    foreach ($service in $services) {
        Write-Step "Starting $($service.Name) on port $($service.Port)"

        $jar = Get-ServiceJar $service.Module
        $logFile = Join-Path $runDirectory "$($service.Name).log"
        $errorLogFile = Join-Path $runDirectory "$($service.Name).error.log"
        Remove-Item -LiteralPath $logFile, $errorLogFile -Force -ErrorAction SilentlyContinue

        $arguments = @(
            "-jar",
            $jar,
            "--server.port=$($service.Port)",
            "--server.address=127.0.0.1",
            "--eureka.instance.prefer-ip-address=true",
            "--eureka.instance.ip-address=127.0.0.1",
            "--eureka.instance.hostname=localhost"
        )
        if ($service.Name -eq "genai-service") {
            $arguments += "--spring.profiles.active=local"
        }
        if ($service.Name -ne "config-server") {
            $arguments += "--spring.cloud.config.enabled=false"
        }

        $process = Start-Process `
            -FilePath "java" `
            -ArgumentList $arguments `
            -WorkingDirectory $root `
            -WindowStyle Hidden `
            -RedirectStandardOutput $logFile `
            -RedirectStandardError $errorLogFile `
            -PassThru

        $entry = [pscustomobject]@{
            Name = $service.Name
            Pid = $process.Id
            Port = $service.Port
            Log = $logFile
        }
        $started += $entry
        $started | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8

        Wait-Service -Service $service -Process $process -LogFile $logFile
        Write-Host "$($service.Name) is ready (PID $($process.Id))." -ForegroundColor Green
    }
}
catch {
    Write-Host ""
    Write-Host "Startup failed. Stopping services started in this run..." -ForegroundColor Red
    foreach ($entry in $started) {
        Stop-Process -Id $entry.Pid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    throw
}

Write-Host ""
Write-Host "Housekeeping platform is running." -ForegroundColor Green
Write-Host "Application: http://localhost:8080"
Write-Host "Eureka:     http://localhost:8761"
Write-Host "Logs:       $runDirectory"
Write-Host ""
Write-Host "Restart later with:"
Write-Host "  .\scripts\start-housekeeping.ps1 -SkipBuild -Restart"
