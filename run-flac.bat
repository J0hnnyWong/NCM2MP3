@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

set "ROOT=%~dp0"
set "SUB=%ROOT%flac-converter"
set "JAR=%SUB%\target\flac-converter-1.0.0.jar"

rem ---------- locate JDK 17 ----------
set "JAVA_HOME_DIR="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME_DIR=%JAVA_HOME%"
if not defined JAVA_HOME_DIR (
  for /D %%D in ("C:\Program Files\Microsoft\jdk-17*") do if not defined JAVA_HOME_DIR set "JAVA_HOME_DIR=%%~fD"
)
if not defined JAVA_HOME_DIR (
  for /D %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if not defined JAVA_HOME_DIR set "JAVA_HOME_DIR=%%~fD"
)
if not defined JAVA_HOME_DIR (
  for /D %%D in ("C:\Program Files\Java\jdk-17*") do if not defined JAVA_HOME_DIR set "JAVA_HOME_DIR=%%~fD"
)
if not defined JAVA_HOME_DIR (
  for /D %%D in ("D:\Development\tools\jdk-17*") do if not defined JAVA_HOME_DIR set "JAVA_HOME_DIR=%%~fD"
)
if not defined JAVA_HOME_DIR (
  echo [ERROR] JDK 17 not found.
  echo         Install it with:  winget install Microsoft.OpenJDK.17
  pause
  exit /b 1
)
set "JAVA=%JAVA_HOME_DIR%\bin\java.exe"
set "PATH=%JAVA_HOME_DIR%\bin;%PATH%"

rem ---------- locate Maven ----------
set "MVN="
where mvn >nul 2>nul && set "MVN=mvn"
if not defined MVN (
  for /D %%D in ("D:\Development\tools\apache-maven-*") do if not defined MVN if exist "%%~fD\bin\mvn.cmd" set "MVN=%%~fD\bin\mvn.cmd"
)
if not defined MVN (
  for /D %%D in ("C:\Program Files\apache-maven-*") do if not defined MVN if exist "%%~fD\bin\mvn.cmd" set "MVN=%%~fD\bin\mvn.cmd"
)

rem ---------- build ----------
set "FORCE=0"
if /I "%~1"=="build" set "FORCE=1"

if "%FORCE%"=="0" if exist "%JAR%" goto :run
if not defined MVN (
  echo [ERROR] jar missing and Maven not found, cannot build.
  echo         Install Maven and put mvn on PATH, or set MVN_HOME.
  pause
  exit /b 1
)
echo [INFO] Building flac-converter with "!MVN!" ...
cd /d "%SUB%"
call "!MVN!" -q package
if errorlevel 1 (
  echo [ERROR] Build failed. See output above.
  pause
  exit /b 1
)
cd /d "%ROOT%"

:run
rem ---------- launch ----------
echo [INFO] Opening FLAC Converter GUI...
"%JAVA%" -Xmx512m -jar "%JAR%"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo [ERROR] Program exited with code %RC%.
  pause
)
endlocal
