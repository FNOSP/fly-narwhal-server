#!/bin/bash

set -euo pipefail

SCRIPT_PATH="$0"
if [[ "$SCRIPT_PATH" != /* ]]; then
  SCRIPT_PATH="$PWD/$SCRIPT_PATH"
fi
SCRIPT_DIR="${SCRIPT_PATH%/*}"
cd "$SCRIPT_DIR"

DEFAULT_OUTPUT_DIR="../fly-narwhal-web/src/main/resources/updater"
OUTPUT_DIR="${OUTPUT_DIR:-${1:-$DEFAULT_OUTPUT_DIR}}"

GO_BIN="${GO_BIN:-}"
if [[ -n "$GO_BIN" && -x "$GO_BIN" ]]; then
  :
elif command -v go >/dev/null 2>&1; then
  GO_BIN="$(command -v go)"
else
  for candidate in "/usr/local/go/bin/go" "/opt/homebrew/bin/go" "/usr/bin/go"; do
    if [[ -x "$candidate" ]]; then
      GO_BIN="$candidate"
      break
    fi
  done
fi

if [[ -z "$GO_BIN" || ! -x "$GO_BIN" ]]; then
  echo "go executable not found. Set GO_BIN or ensure go is on PATH." >&2
  exit 1
fi

/bin/mkdir -p "$OUTPUT_DIR"

echo "Building for Linux amd64..."
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 "$GO_BIN" build -trimpath -ldflags "-s -w" -o "$OUTPUT_DIR/updater-linux-amd64" ./cmd/main.go

echo "Building for Linux arm64..."
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 "$GO_BIN" build -trimpath -ldflags "-s -w" -o "$OUTPUT_DIR/updater-linux-aarch64" ./cmd/main.go

echo "Build complete. Binaries placed in $OUTPUT_DIR"
