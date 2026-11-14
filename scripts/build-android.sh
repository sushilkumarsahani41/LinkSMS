#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -d "$PROJECT_DIR/work/jdk/Contents/Home" ]; then
  export JAVA_HOME="$PROJECT_DIR/work/jdk/Contents/Home"
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi
if [ -x "$PROJECT_DIR/work/gradle-8.11.1/bin/gradle" ]; then
  exec "$PROJECT_DIR/work/gradle-8.11.1/bin/gradle" -p "$PROJECT_DIR/android" assembleDebug lintDebug "$@"
fi
exec "$PROJECT_DIR/android/gradlew" -p "$PROJECT_DIR/android" assembleDebug lintDebug "$@"
