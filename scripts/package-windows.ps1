param(
    [string]$JavaHome = "C:\Program Files\Zulu\zulu-17"
)

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not (Test-Path $JavaHome)) {
    throw "JavaHome path not found: $JavaHome"
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "Using JAVA_HOME: $env:JAVA_HOME"
$mvn = "D:\tools\apache-maven-3.9.15-bin\apache-maven-3.9.15\bin\mvn.cmd"
if (-not (Test-Path $mvn)) {
    throw "Maven not found at $mvn"
}

Write-Host "Building application JAR..."
& $mvn -q -DskipTests clean package
if ($LASTEXITCODE -ne 0) {
    throw "Maven package failed with exit code $LASTEXITCODE"
}

Write-Host "Copying runtime dependencies..."
& $mvn -q -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\libs
if ($LASTEXITCODE -ne 0) {
    throw "Maven dependency copy failed with exit code $LASTEXITCODE"
}

$iconDir = Join-Path $projectRoot "packaging"
New-Item -ItemType Directory -Force -Path $iconDir | Out-Null
$iconPath = Join-Path $iconDir "app.ico"

Write-Host "Generating app icon (.ico)..."
Add-Type -AssemblyName System.Drawing
$size = 256
$bmp = New-Object System.Drawing.Bitmap $size, $size
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$rect = New-Object System.Drawing.Rectangle 0,0,$size,$size
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, [System.Drawing.Color]::FromArgb(24,94,184), [System.Drawing.Color]::FromArgb(43,128,255), 45)
$g.FillRectangle($brush, $rect)
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(245,255,255), 16)
$pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawLine($pen, 64, 78, 64, 178)
$g.DrawLine($pen, 64, 78, 188, 78)
$g.DrawLine($pen, 64, 128, 162, 128)
$g.DrawLine($pen, 64, 178, 210, 178)
$hIcon = $bmp.GetHicon()
$icon = [System.Drawing.Icon]::FromHandle($hIcon)
$fs = [System.IO.File]::Open($iconPath, [System.IO.FileMode]::Create)
$icon.Save($fs)
$fs.Dispose()
$g.Dispose()
$brush.Dispose()
$pen.Dispose()
$bmp.Dispose()

$staging = Join-Path $projectRoot "target\jpackage-input"
if (Test-Path $staging) {
    Remove-Item -Recurse -Force $staging
}
New-Item -ItemType Directory -Force -Path $staging | Out-Null

Copy-Item "target\javalin-vue-app-1.0-SNAPSHOT.jar" $staging
Copy-Item "target\libs\*.jar" $staging

$dest = Join-Path $projectRoot "dist"
if (Test-Path $dest) {
    Remove-Item -Recurse -Force $dest
}
New-Item -ItemType Directory -Force -Path $dest | Out-Null

Write-Host "Packaging native EXE..."
$wixLight = Get-Command light.exe -ErrorAction SilentlyContinue
$wixCandle = Get-Command candle.exe -ErrorAction SilentlyContinue
$packageType = "app-image"

if ($wixLight -and $wixCandle) {
    $packageType = "exe"
    Write-Host "WiX detected. Building EXE installer..."
} else {
    Write-Warning "WiX not found (light.exe/candle.exe). Falling back to app-image."
}

$args = @(
    "--type", $packageType,
    "--name", "EMRWorkspace",
    "--input", $staging,
    "--main-jar", "javalin-vue-app-1.0-SNAPSHOT.jar",
    "--main-class", "com.example.desktop.Launcher",
    "--icon", $iconPath,
    "--dest", $dest,
    "--app-version", "1.0.0",
    "--win-console"
)

if ($packageType -eq "exe") {
    $args += "--win-dir-chooser"
    $args += "--win-shortcut"
}

& jpackage @args
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE"
}

Write-Host "Done. Package type: $packageType"
Write-Host "Output directory: $dest"
