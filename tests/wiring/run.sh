#!/bin/bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 11+ installation}"
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
  echo "JDK not found: $JAVA_HOME" >&2
  exit 2
fi
OUT="${WIRING_BUILD_DIR:-$ROOT/tests/wiring/.build}"
PRODUCTION_SOURCE_DIR="${PRODUCTION_SOURCE_DIR:-$ROOT/src/com/malhaedwo/pttprobe}"
rm -rf "$OUT"
mkdir -p "$OUT"
PRODUCTION=(
  "$PRODUCTION_SOURCE_DIR/CalendarReliability.java"
  "$PRODUCTION_SOURCE_DIR/CaptureItem.java"
  "$PRODUCTION_SOURCE_DIR/ParsedCommand.java"
  "$PRODUCTION_SOURCE_DIR/RemoteCapture.java"
  "$PRODUCTION_SOURCE_DIR/SyncOperation.java"
  "$PRODUCTION_SOURCE_DIR/BackupBeforeMigration.java"
  "$PRODUCTION_SOURCE_DIR/CaptureDatabase.java"
  "$PRODUCTION_SOURCE_DIR/CalendarSync.java"
  "$PRODUCTION_SOURCE_DIR/ReminderScheduler.java"
  "$PRODUCTION_SOURCE_DIR/ReminderReceiver.java"
  "$PRODUCTION_SOURCE_DIR/CaptureAutomation.java"
)
mapfile_compat() { while IFS= read -r line; do SUPPORT+=("$line"); done; }
SUPPORT=()
mapfile_compat < <(find "$ROOT/tests/wiring/support" "$ROOT/tests/wiring/src" -name '*.java' -print | sort)
"$JAVA_HOME/bin/javac" -encoding UTF-8 --release 11 -d "$OUT" "${SUPPORT[@]}" "${PRODUCTION[@]}"
"$JAVA_HOME/bin/java" -ea -cp "$OUT" com.malhaedwo.pttprobe.WiringTest
"$JAVA_HOME/bin/java" -ea -cp "$OUT" com.malhaedwo.pttprobe.BackupBeforeMigrationTest

"$JAVA_HOME/bin/java" -ea -cp "$OUT" com.malhaedwo.pttprobe.RemoteDeleteWiringTest
