#!/bin/bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 11+ installation}"
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "JDK not found: $JAVA_HOME" >&2
  exit 2
fi
OUT="$ROOT/tests/insets/build"
PRODUCTION_SOURCE_DIR="${PRODUCTION_SOURCE_DIR:-$ROOT/src/com/malhaedwo/pttprobe}"
rm -rf "$OUT"
mkdir -p "$OUT"
SOURCES=()
while IFS= read -r file; do SOURCES+=("$file"); done < <(find "$ROOT/tests/insets/support" "$ROOT/tests/insets/src" -name '*.java' -print | sort)
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 11 -d "$OUT" "${SOURCES[@]}" "$PRODUCTION_SOURCE_DIR/SystemBarInsets.java"
"$JAVA_HOME/bin/java" -ea -cp "$OUT" com.malhaedwo.pttprobe.SystemBarInsetsTest "$PRODUCTION_SOURCE_DIR"
