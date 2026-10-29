#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://services.gradle.org/distributions/gradle-8.13-wrapper.jar"
if [ ! -s "$WRAPPER_JAR" ]; then
  echo "Downloading official Gradle 8.13 wrapper (first run only)..." >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fLsS "$WRAPPER_URL" -o "$WRAPPER_JAR" || exit 1
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR" || exit 1
  else
    echo "curl or wget is required to bootstrap the Gradle wrapper." >&2
    exit 1
  fi
fi
exec java -cp "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
