#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
repository="$PWD/build/consumer-repo"
version="${1:?Expected publication version}"
for sample in starter-minimal quarkus-minimal; do
  ./gradlew -p "samples/$sample" \
    "-Pmuyun.consumer.repository=$repository" \
    "-Pmuyun.consumer.version=$version" build
done
