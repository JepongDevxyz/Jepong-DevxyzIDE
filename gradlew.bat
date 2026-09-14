@echo off
set DIR=%~dp0
if not exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  echo DevxyzIDE source: gradle-wrapper.jar is not bundled in this archive.
  echo Use trusted Gradle 9.6.0 to run: gradle wrapper --gradle-version 9.6.0
  exit /b 1
)
java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
