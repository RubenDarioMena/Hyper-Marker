#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then
    JAVA_HOME=/opt/homebrew/opt/openjdk@17
    export JAVA_HOME
fi
./gradlew testDebugUnitTest lintDebug assembleDebug "$@"
