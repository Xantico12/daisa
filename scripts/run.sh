#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: ./scripts/run.sh /path/to/obsidian-vault" >&2
  exit 2
fi

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/build/classes"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

find "$ROOT_DIR/src/main/java" -name "*.java" | sort > "$ROOT_DIR/build/main-sources.txt"
javac -encoding UTF-8 -d "$BUILD_DIR" @"$ROOT_DIR/build/main-sources.txt"
java -cp "$BUILD_DIR" daisa.App "$1"

