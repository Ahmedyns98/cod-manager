@REM ----------------------------------------------------------------------------
@REM Maven wrapper for Windows.
@REM
@REM Downloads Maven into %USERPROFILE%\.m2\wrapper on first run, then hands the
@REM command over to it. Nothing has to be installed but a JDK, and the version
@REM used is pinned in .mvn\wrapper\maven-wrapper.properties so every machine
@REM builds with the same one.
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set MAVEN_VERSION=3.9.9
set WRAPPER_HOME=%USERPROFILE%\.m2\wrapper
set MAVEN_HOME=%WRAPPER_HOME%\apache-maven-%MAVEN_VERSION%
set MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd

if not defined JAVA_HOME (
  echo.
  echo   JAVA_HOME is not set. Point it at your JDK 21, for example:
  echo     $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
  echo.
  exit /b 1
)

if exist "%MAVEN_BIN%" goto run

echo Downloading Maven %MAVEN_VERSION% once into %WRAPPER_HOME% ...
if not exist "%WRAPPER_HOME%" mkdir "%WRAPPER_HOME%"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$url = 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip';" ^
  "$zip = Join-Path $env:TEMP 'apache-maven.zip';" ^
  "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;" ^
  "Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing;" ^
  "Expand-Archive -Path $zip -DestinationPath '%WRAPPER_HOME%' -Force;" ^
  "Remove-Item $zip -Force"

if not exist "%MAVEN_BIN%" (
  echo.
  echo   The download failed. Check the connection and run this again -- it resumes
  echo   from nothing, so a second attempt is safe.
  echo.
  exit /b 1
)

:run
call "%MAVEN_BIN%" %*
