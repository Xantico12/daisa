#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/build/test-classes"

rm -rf "$ROOT_DIR/build"
mkdir -p "$BUILD_DIR"

find "$ROOT_DIR/src/main/java" "$ROOT_DIR/src/test/java" -name "*.java" | sort > "$ROOT_DIR/build/sources.txt"
javac -encoding UTF-8 -d "$BUILD_DIR" @"$ROOT_DIR/build/sources.txt"
java -cp "$BUILD_DIR" daisa.TestRunner

