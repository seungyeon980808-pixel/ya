#!/bin/bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 11+ installation}"
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "JDK not found: $JAVA_HOME" >&2
  exit 2
fi
OUT="$ROOT/tests/ptt/.build"
SRC="${PRODUCTION_SOURCE_DIR:-$ROOT/src/com/malhaedwo/pttprobe}"
rm -rf "$OUT"; mkdir -p "$OUT"
SOURCES=()
while IFS= read -r f; do SOURCES+=("$f"); done < <(find "$ROOT/tests/ptt/support" "$ROOT/tests/ptt/src" -name '*.java' | sort)
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 11 -d "$OUT" "${SOURCES[@]}" "$SRC/PttReadiness.java" "$SRC/PttService.java" "$SRC/PttStore.java" "$SRC/VolumeKeyService.java"
"$JAVA_HOME/bin/java" -ea -cp "$OUT" com.malhaedwo.pttprobe.PttLifecycleTest "$SRC"
