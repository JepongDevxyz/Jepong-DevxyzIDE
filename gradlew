#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$CLASSPATH" ]; then
  echo "DevxyzIDE source: gradle-wrapper.jar is not bundled in this archive." >&2
  echo "Use trusted Gradle 9.6.0 to run: gradle wrapper --gradle-version 9.6.0" >&2
  exit 1
fi
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
