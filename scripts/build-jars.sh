#!/usr/bin/env bash
set -euo pipefail
# No -T: Maven builds the reactor sequentially, in one bounded JVM.
# Tests are a separate gate; this local image-build step intentionally skips them.
mkdir -p build/lite-jars
mvn -B -ntp -Dmaven.test.skip=true package
for service in account ledger fraud notification payment gateway; do
  cp "$service-service/target/$service-service-1.0.0.jar" "build/lite-jars/$service.jar"
done
