#!/usr/bin/env bash
set -euo pipefail
# Uses only a fresh disposable database. Never connects to application credentials.
PG_BIN="${PG_BIN:-/opt/homebrew/opt/postgresql@16/bin}"
TEST_PORT="${TEST_PORT:-55439}"
TEST_ROOT="$(mktemp -d /private/tmp/fieldnotes-test.XXXXXX)"
cleanup() {
  "$PG_BIN/pg_ctl" -D "$TEST_ROOT/data" -m immediate stop >/dev/null 2>&1 || true
  rm -rf "$TEST_ROOT"
}
trap cleanup EXIT
"$PG_BIN/initdb" -D "$TEST_ROOT/data" -U fieldnotes_test --auth=trust > "$TEST_ROOT/init.log"
"$PG_BIN/pg_ctl" -D "$TEST_ROOT/data" -l "$TEST_ROOT/server.log" -o "-h 127.0.0.1 -p $TEST_PORT -k $TEST_ROOT" -w start
"$PG_BIN/createdb" -h 127.0.0.1 -p "$TEST_PORT" -U fieldnotes_test fieldnotes_test
TEST_DB_URL="jdbc:postgresql://127.0.0.1:$TEST_PORT/fieldnotes_test" TEST_DB_USERNAME=fieldnotes_test TEST_DB_PASSWORD='' ./gradlew test --no-daemon --rerun-tasks
