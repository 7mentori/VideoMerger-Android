@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not installed. Install Gradle 9.6.0, then run:
  echo gradle wrapper --gradle-version 9.6.0 --distribution-type bin
  echo This project ZIP does not contain gradle-wrapper.jar.
  exit /b 127
)
gradle %*
