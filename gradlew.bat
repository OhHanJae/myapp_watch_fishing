@echo off
setlocal
set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
set "WRAPPER_URL=https://services.gradle.org/distributions/gradle-8.13-wrapper.jar"
if not exist "%WRAPPER_JAR%" (
  echo Downloading official Gradle 8.13 wrapper ^(first run only^)...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
  if errorlevel 1 (
    echo Failed to download Gradle wrapper.
    exit /b 1
  )
)
java -cp "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
