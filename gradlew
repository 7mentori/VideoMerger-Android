#!/usr/bin/env sh
# Bootstrap fallback only. This is NOT the official Gradle Wrapper launcher.
set -eu
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is not installed. Install Gradle 9.6.0, then run: gradle wrapper --gradle-version 9.6.0 --distribution-type bin" >&2
  echo "This project ZIP does not contain gradle-wrapper.jar." >&2
  exit 127
fi
exec gradle "$@"
