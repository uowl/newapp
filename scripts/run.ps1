$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# Change to the project root (parent of this scripts directory)
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $ProjectRoot

function Resolve-MavenCommand {
    $mavenCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mavenCmd) {
        return "mvn"
    }

    $fallback = "D:\tools\apache-maven-3.9.15-bin\apache-maven-3.9.15\bin\mvn.cmd"
    if (Test-Path -LiteralPath $fallback) {
        return $fallback
    }

    return $null
}

# Prerequisite checks
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    Write-Error "ERROR: 'npm' not found. Install Node.js first."
}

# Prefer installed Zulu 25 for this project run.
$zulu25Home = "C:\Program Files\Zulu\zulu-25"
if (-not (Test-Path -LiteralPath $zulu25Home)) {
    Write-Error "ERROR: Zulu 25 not found at '$zulu25Home'."
}
$env:JAVA_HOME = $zulu25Home
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$mavenExe = Resolve-MavenCommand
if (-not $mavenExe) {
    Write-Error "ERROR: 'mvn' not found. Install Maven or add it to PATH."
}

# Log setup
$logsDir = Join-Path $ProjectRoot "logs"
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logFile = Join-Path $logsDir "run-$timestamp.log"
Start-Transcript -Path $logFile -Append | Out-Null

# JavaFX/WebView on some Windows driver stacks can crash with native access violations
# (exit code -1073741819 / 0xC0000005). Force software rendering and persist crash artifacts.
$javaCrashFile = (Join-Path $logsDir "hs_err_pid%p.log").Replace("\", "/")
$javaOpts = @(
    "-Dprism.order=sw",
    "-Dsun.java2d.d3d=false",
    "-Dsun.java2d.noddraw=true",
    "-XX:ErrorFile=$javaCrashFile",
    "-XX:HeapDumpPath=$logsDir"
)

if ($env:JDK_JAVA_OPTIONS) {
    $env:JDK_JAVA_OPTIONS = "$env:JDK_JAVA_OPTIONS $($javaOpts -join ' ')"
} else {
    $env:JDK_JAVA_OPTIONS = $javaOpts -join " "
}

# Default to embedded WebView unless explicitly overridden by caller.
if (-not $env:APP_DISABLE_WEBVIEW) {
    $env:APP_DISABLE_WEBVIEW = "0"
}

try {
    Write-Host "Log file: $logFile"
    Write-Host "Started : $(Get-Date)"
    Write-Host "JAVA_HOME: $env:JAVA_HOME"
    Write-Host "JDK_JAVA_OPTIONS: $env:JDK_JAVA_OPTIONS"
    Write-Host "APP_DISABLE_WEBVIEW: $env:APP_DISABLE_WEBVIEW"
    Write-Host ""

    Write-Host "==================================="
    Write-Host "1. Building Vue Frontend"
    Write-Host "==================================="

    $publicDir = Join-Path $ProjectRoot "src\main\resources\public"
    if (Test-Path -LiteralPath $publicDir) {
        Remove-Item -LiteralPath $publicDir -Recurse -Force
    }

    Push-Location (Join-Path $ProjectRoot "frontend")
    try {
        npm install
        if ($LASTEXITCODE -ne 0) {
            throw "npm install failed with exit code $LASTEXITCODE."
        }

        npm run build
        if ($LASTEXITCODE -ne 0) {
            throw "npm run build failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }

    Write-Host ""
    Write-Host "==================================="
    Write-Host "2. Running JavaFX Desktop App"
    Write-Host "==================================="
    & $mavenExe clean compile javafx:run
    if ($LASTEXITCODE -ne 0) {
        throw "Maven run failed with exit code $LASTEXITCODE."
    }
} finally {
    Stop-Transcript | Out-Null
}
