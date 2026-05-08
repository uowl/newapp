#!/bin/bash
set -euo pipefail

# Change to the project root (directory containing this script)
cd "$(dirname "$0")"

# ─── Prerequisite Checks ─────────────────────────────────────────────────────
if ! command -v npm &> /dev/null; then
  echo "ERROR: 'npm' not found. Install Node.js first:"
  echo "  sudo dnf install -y nodejs   # Fedora/RHEL"
  echo "  sudo apt install -y nodejs   # Debian/Ubuntu"
  exit 1
fi

if ! command -v mvn &> /dev/null; then
  echo "ERROR: 'mvn' not found. Install Maven first:"
  echo "  sudo dnf install -y maven    # Fedora/RHEL"
  echo "  sudo apt install -y maven    # Debian/Ubuntu"
  exit 1
fi

# ─── Log setup ───────────────────────────────────────────────────────────────
mkdir -p logs
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
LOG_FILE="logs/run-${TIMESTAMP}.log"

# Tee all subsequent output (stdout + stderr) to the log file
exec > >(tee -a "$LOG_FILE") 2>&1

echo "Log file: $LOG_FILE"
echo "Started : $(date)"
echo ""

# ─── 1. Build Vue Frontend ───────────────────────────────────────────────────
echo "==================================="
echo "1. Building Vue Frontend"
echo "==================================="
# Vite outputs directly to src/main/resources/public (see vite.config.js outDir).
# Clean that directory BEFORE the build so Vite's emptyOutDir starts fresh.
rm -rf src/main/resources/public

cd frontend
npm install
npm run build
cd ..

# ─── 2. Run JavaFX Desktop App ───────────────────────────────────────────────
echo ""
echo "==================================="
echo "2. Running JavaFX Desktop App"
echo "==================================="
# javafx-maven-plugin automatically adds --module-path and --add-modules for
# JavaFX 21. Use this goal instead of exec:java to ensure the JVM module system
# is configured correctly on Java 21+.
mvn clean compile javafx:run
