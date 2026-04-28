#!/bin/bash

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 LBM.jar [PROGRAM_ARGS]"
    exit 1
fi

PROGRAM=$1
shift 1

JAVA_BIN=${JAVA_BIN:-java}
JAVA_OPTS=${JAVA_OPTS:-"-Xmx32g -Xms4g"}

exec "$JAVA_BIN" $JAVA_OPTS -jar "$PROGRAM" "$@" NUMNODES 1 CLUST_FILE clusters6386nodist.csv
