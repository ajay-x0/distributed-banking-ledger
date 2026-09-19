#!/usr/bin/env bash
# A small Bash HTTP probe avoids starting another JVM for each health check.
set -euo pipefail
exec 3<>/dev/tcp/127.0.0.1/9090
printf 'GET /actuator/health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
IFS= read -r status <&3
[[ "$status" == *" 200 "* ]]
