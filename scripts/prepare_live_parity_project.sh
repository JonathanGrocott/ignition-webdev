#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${1:-/tmp/ignition-web-live-parity}"
PROJECT_NAME="${2:-webcompat_live_parity}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_DIR="$ROOT_DIR/fixtures/live-parity/project-template"
MOUNTED_DIR="$ROOT_DIR/fixtures/live-parity/mounted-content"
PROJECT_DIR="$OUT_DIR/$PROJECT_NAME"

if [[ ! -d "$TEMPLATE_DIR" ]]; then
  echo "Template directory missing: $TEMPLATE_DIR" >&2
  exit 1
fi

rm -rf "$PROJECT_DIR"
mkdir -p "$OUT_DIR"
cp -R "$TEMPLATE_DIR" "$PROJECT_DIR"

MOUNTED_CONFIG="$PROJECT_DIR/com.inductiveautomation.webdev/resources/mounted/config.json"
MOUNTED_DIR="$MOUNTED_DIR" perl -0pi -e 's/__MOUNTED_PATH__/$ENV{MOUNTED_DIR}/g' "$MOUNTED_CONFIG"

echo "Prepared project: $PROJECT_DIR"
echo "Mounted content path: $MOUNTED_DIR"
echo
echo "Import this folder into Ignition as project name '$PROJECT_NAME':"
echo "  $PROJECT_DIR"
echo
echo "Then run:"
echo "  scripts/parity_phase2_live.sh http://localhost:8088 $PROJECT_NAME"
