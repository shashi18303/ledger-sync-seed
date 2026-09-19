#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " Simplify Money · Ledger Sync Verification"
echo "=========================================="

echo "[1/4] Running Gradle build and JUnit test suite..."
./gradlew test --info

echo "[2/4] Ingesting fixtures/corpus-a.jsonl and generating output reports..."
./gradlew run --args="ingest fixtures/corpus-a.jsonl output/"

echo "[3/4] Running self-check against fixtures/corpus-a-totals.json..."
./gradlew run --args="self-check fixtures/corpus-a-totals.json output/"

echo "[4/4] Executing Backfill and ConsistencyChecker (SQL -> MongoDB)..."
./gradlew run --args="verify-store"

echo "=========================================="
echo " ALL CHECKS PASSED: LEDGER IS PRODUCTION READY "
echo "=========================================="
