#!/bin/bash

#add/remove based on necessary boiler plate code for using java in your system. Must be at least jdk1.8
# Load Java via environment modules where available
if command -v module >/dev/null 2>&1; then module load jdk; fi
#BAW module load jdk
#

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 LBM.jar [PROGRAM_ARGS]"
    exit 1
fi

PROGRAM=$1
shift 1

JAVA_BIN=${JAVA_BIN:-java}
JAVA_OPTS=${JAVA_OPTS:-"-Xmx32g -Xms4g"}

exec "$JAVA_BIN" $JAVA_OPTS -jar "$PROGRAM" "$@" 
