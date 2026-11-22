#!/bin/sh
# Builds the signed release APK. Signing config is read from ~/.linksms-signing/keystore.properties
# (or $LINKSMS_SIGNING_PROPERTIES); keep that file and the keystore out of the repository.
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -d "$PROJECT_DIR/work/jdk/Contents/Home" ]; then export JAVA_HOME="$PROJECT_DIR/work/jdk/Contents/Home"; fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then export ANDROID_HOME="$HOME/Library/Android/sdk"; fi
GRADLE="$PROJECT_DIR/android/gradlew"
if [ -x "$PROJECT_DIR/work/gradle-8.11.1/bin/gradle" ]; then GRADLE="$PROJECT_DIR/work/gradle-8.11.1/bin/gradle"; fi
exec "$GRADLE" -p "$PROJECT_DIR/android" testReleaseUnitTest lintRelease assembleRelease "$@"
