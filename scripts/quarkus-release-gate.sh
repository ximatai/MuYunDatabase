#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLEW="${ROOT_DIR}/gradlew"

usage() {
  printf '%s\n' \
    "Usage:" \
    "  bash scripts/quarkus-release-gate.sh <mode>" \
    "" \
    "Modes:" \
    "  jvm             Run the default JVM test suite." \
    "  jvm-postgres    Require the Quarkus PostgreSQL Testcontainers matrix." \
    "  native-h2       Build and run the Quarkus H2 native smoke." \
    "  native-postgres Build and run the Quarkus PostgreSQL native smoke." \
    "  release         Run jvm, jvm-postgres, native-h2, and native-postgres." \
    "" \
    "native-postgres requires:" \
    "  MUYUN_NATIVE_POSTGRES_JDBC_URL   e.g. jdbc:postgresql://127.0.0.1:5432/muyun_native" \
    "  MUYUN_NATIVE_POSTGRES_USERNAME" \
    "" \
    "Optional:" \
    "  MUYUN_NATIVE_POSTGRES_PASSWORD   defaults to empty" \
    "  MUYUN_NATIVE_POSTGRES_SCHEMA     defaults to public"
}

run_jvm() {
  "${GRADLEW}" test
}

run_jvm_postgres() {
  "${GRADLEW}" :muyun-database-quarkus-integration-test:test \
    -Pmuyun.postgres.it.required=true
}

run_native_h2() {
  "${GRADLEW}" :muyun-database-quarkus-integration-test:testNative \
    -Dquarkus.native.enabled=true \
    -Dquarkus.package.jar.enabled=false
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: ${name}" >&2
    exit 2
  fi
}

run_native_postgres() {
  require_env MUYUN_NATIVE_POSTGRES_JDBC_URL
  require_env MUYUN_NATIVE_POSTGRES_USERNAME

  local password="${MUYUN_NATIVE_POSTGRES_PASSWORD:-}"
  local schema="${MUYUN_NATIVE_POSTGRES_SCHEMA:-public}"

  "${GRADLEW}" :muyun-database-quarkus-integration-test:testNative \
    -Dquarkus.native.enabled=true \
    -Dquarkus.package.jar.enabled=false \
    -Dmuyun.native.postgres.enabled=true \
    -Dquarkus.datasource.db-kind=postgresql \
    -Dquarkus.datasource.jdbc.url="${MUYUN_NATIVE_POSTGRES_JDBC_URL}" \
    -Dquarkus.datasource.username="${MUYUN_NATIVE_POSTGRES_USERNAME}" \
    -Dquarkus.datasource.password="${password}" \
    -Dquarkus.test.arg-line="-Dmuyun.database.default-schema=${schema} -Dmuyun.database.install-postgres-plugins=true"
}

mode="${1:-}"
case "${mode}" in
  jvm)
    run_jvm
    ;;
  jvm-postgres)
    run_jvm_postgres
    ;;
  native-h2)
    run_native_h2
    ;;
  native-postgres)
    run_native_postgres
    ;;
  release)
    run_jvm
    run_jvm_postgres
    run_native_h2
    run_native_postgres
    ;;
  -h|--help|help)
    usage
    ;;
  "")
    usage >&2
    exit 2
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
