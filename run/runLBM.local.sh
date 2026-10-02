#!/bin/bash

# add/remove based on necessary boiler plate code for using java in your system. Must be at least jdk1.8
# Load Java via environment modules where available
if command -v module >/dev/null 2>&1; then module load jdk; fi

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 LBM.jar [MODEL stochastic|continuous] [PROGRAM_ARGS]"
    exit 1
fi

PROGRAM=$1
shift

JAVA_BIN=${JAVA_BIN:-java}
JAVA_OPTS=${JAVA_OPTS:-"-Xmx32g -Xms4g"}

# MODEL is optional and may occur anywhere among the NAME VALUE argument pairs.
# Existing commands without MODEL remain stochastic and behave exactly as before.
MODEL="stochastic"
ARGS=()

while [ $# -gt 0 ]; do
    if [ $# -lt 2 ]; then
        echo "Program arguments must be NAME VALUE pairs" >&2
        exit 1
    fi

    KEY=$1
    VALUE=$2
    shift 2

    if [ "$(printf '%s' "$KEY" | tr '[:lower:]' '[:upper:]')" = "MODEL" ]; then
        MODEL=$(printf '%s' "$VALUE" | tr '[:upper:]' '[:lower:]')
    else
        ARGS+=("$KEY" "$VALUE")
    fi
done

case "$MODEL" in
    stochastic)
        exec "$JAVA_BIN" $JAVA_OPTS -jar "$PROGRAM" "${ARGS[@]}"
        ;;
    continuous)
        exec "$JAVA_BIN" $JAVA_OPTS -cp "$PROGRAM" continuous.ContinuousRunner "${ARGS[@]}"
        ;;
    *)
        echo "Unknown MODEL '$MODEL' (use stochastic or continuous)" >&2
        exit 1
        ;;
esac
