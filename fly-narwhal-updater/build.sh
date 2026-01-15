#!/bin/bash

# Output directory in the web module resources
OUTPUT_DIR="../fly-narwhal-web/src/main/resources/updater"

mkdir -p "$OUTPUT_DIR"

echo "Building for Linux amd64..."
GOOS=linux GOARCH=amd64 go build -o "$OUTPUT_DIR/updater-linux-amd64" cmd/main.go

echo "Building for Linux arm64..."
GOOS=linux GOARCH=arm64 go build -o "$OUTPUT_DIR/updater-linux-aarch64" cmd/main.go

echo "Build complete. Binaries placed in $OUTPUT_DIR"
