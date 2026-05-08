param(
    [string]$JavaHome  = "C:\Program Files\Zulu\zulu-21",
    [string]$MavenHome = "",          # Leave empty to auto-detect via PATH
    [switch]$WinConsole               # Pass -WinConsole to attach a debug terminal window
)

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

# ─── Java 21 ─────────────────────────────────────────────────────────────────
if (-not (Test-Path $JavaHome)) {
    throw "JavaHome path not found: $JavaHome`nDownload Zulu JDK 21 from https://www.azul.com/downloads/"
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Write-Host "Using JAVA_HOME: $env:JAVA_HOME"

# ─── Node / npm ───────────────────────────────────────────────────────────────
$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
if (-not $npm) { $npm = Get-Command npm -ErrorAction SilentlyContinue }
if (-not $npm) {
    throw "'npm' not found. Install Node.js (LTS) from https://nodejs.org/ and ensure it is on PATH."
}
Write-Host "Using npm: $($npm.Source)"

# ─── Maven ────────────────────────────────────────────────────────────────────
if ($MavenHome -ne "") {
    $mvn = Join-Path $MavenHome "bin\mvn.cmd"
} else {
    # Try PATH first
    $mvnCmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $mvnCmd) { $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue }
    if ($mvnCmd) {
        $mvn = $mvnCmd.Source
    } else {
        # Last-resort hardcoded fallback
        $mvn = "D:\tools\apache-maven-3.9.15-bin\apache-maven-3.9.15\bin\mvn.cmd"
    }
}
if (-not (Test-Path $mvn)) {
    throw "Maven not found at '$mvn'.`nInstall Maven and add it to PATH, or pass -MavenHome 'C:\path\to\maven'."
}
Write-Host "Using Maven: $mvn"

# ─── 1. Build Vue frontend ────────────────────────────────────────────────────
# src/main/resources/public/ is .gitignored (built artifact).
# It MUST be built here so Maven packages the frontend into the JAR.
Write-Host ""
Write-Host "=== Building Vue frontend ==="
$frontendDir = Join-Path $projectRoot "frontend"
Set-Location $frontendDir
& $npm install --prefer-offline
if ($LASTEXITCODE -ne 0) { throw "npm install failed with exit code $LASTEXITCODE" }
& $npm run build
if ($LASTEXITCODE -ne 0) { throw "npm build failed with exit code $LASTEXITCODE" }
Set-Location $projectRoot

# ─── 2. Build application JAR ─────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Building application JAR ==="
& $mvn -q -DskipTests clean package
if ($LASTEXITCODE -ne 0) { throw "Maven package failed with exit code $LASTEXITCODE" }

# ─── 3. Copy runtime dependencies ────────────────────────────────────────────
# Includes JavaFX 21 native JARs (win classifier) needed by jpackage.
Write-Host "Copying runtime dependencies..."
& $mvn -q -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\libs
if ($LASTEXITCODE -ne 0) { throw "Maven dependency copy failed with exit code $LASTEXITCODE" }

# ─── 4. Generate app icon (.ico) ─────────────────────────────────────────────
$iconDir  = Join-Path $projectRoot "packaging"
$iconPath = Join-Path $iconDir "app.ico"
New-Item -ItemType Directory -Force -Path $iconDir | Out-Null

Write-Host "Generating app icon..."
Add-Type -AssemblyName System.Drawing
$size   = 256
$bmp    = New-Object System.Drawing.Bitmap $size, $size
$g      = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$rect   = New-Object System.Drawing.Rectangle 0, 0, $size, $size
$brush  = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
            $rect,
            [System.Drawing.Color]::FromArgb(24, 94, 184),
            [System.Drawing.Color]::FromArgb(43, 128, 255),
            45)
$g.FillRectangle($brush, $rect)
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(245, 255, 255), 16)
$pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawLine($pen, 64, 78, 64, 178)
$g.DrawLine($pen, 64, 78, 188, 78)
$g.DrawLine($pen, 64, 128, 162, 128)
$g.DrawLine($pen, 64, 178, 210, 178)
$hIcon = $bmp.GetHicon()
$icon  = [System.Drawing.Icon]::FromHandle($hIcon)
$fs    = [System.IO.File]::Open($iconPath, [System.IO.FileMode]::Create)
$icon.Save($fs)
$fs.Dispose(); $g.Dispose(); $brush.Dispose(); $pen.Dispose(); $bmp.Dispose()

# ─── 5. Stage JARs for jpackage ──────────────────────────────────────────────
$staging = Join-Path $projectRoot "target\jpackage-input"
if (Test-Path $staging) { Remove-Item -Recurse -Force $staging }
New-Item -ItemType Directory -Force -Path $staging | Out-Null

Copy-Item "target\javalin-vue-app-1.0-SNAPSHOT.jar" $staging
Copy-Item "target\libs\*.jar" $staging

# ─── 6. Package native EXE ───────────────────────────────────────────────────
$dest = Join-Path $projectRoot "dist"
if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
New-Item -ItemType Directory -Force -Path $dest | Out-Null

Write-Host ""
Write-Host "=== Packaging native EXE ==="
$wixLight  = Get-Command light.exe  -ErrorAction SilentlyContinue
$wixCandle = Get-Command candle.exe -ErrorAction SilentlyContinue
$packageType = "app-image"

if ($wixLight -and $wixCandle) {
    $packageType = "exe"
    Write-Host "WiX detected. Building EXE installer..."
} else {
    Write-Warning "WiX not found (light.exe/candle.exe). Falling back to app-image."
}

$jpackageArgs = @(
    "--type",       $packageType,
    "--name",       "EMRWorkspace",
    "--input",      $staging,
    "--main-jar",   "javalin-vue-app-1.0-SNAPSHOT.jar",
    "--main-class", "com.example.desktop.Launcher",
    "--icon",       $iconPath,
    "--dest",       $dest,
    "--app-version","1.0.0",
    # JavaFX 21 requires --add-modules so the module system opens the graphics pipeline
    "--java-options", "--add-modules javafx.controls,javafx.web"
)

# --win-console attaches a debug terminal window. Off by default; pass -WinConsole to enable.
if ($WinConsole) { $jpackageArgs += "--win-console" }

if ($packageType -eq "exe") {
    $jpackageArgs += "--win-dir-chooser"
    $jpackageArgs += "--win-shortcut"
}

& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }

Write-Host ""
Write-Host "Done. Package type : $packageType"
Write-Host "Output directory   : $dest"
