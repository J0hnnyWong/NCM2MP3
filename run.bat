@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

set "ROOT=%~dp0"
set "JAR=%ROOT%target\NCM2MP3-3.1.0.jar"
cd /d "%ROOT%"

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
echo [INFO] Building with "!MVN!" ...
call "!MVN!" -q package
if errorlevel 1 (
  echo [ERROR] Build failed. See output above.
  pause
  exit /b 1
)

:run
rem ---------- launch ----------
if "%FORCE%"=="1" shift
set "ARGS="
:collect
if "%~1"=="" goto :do
set "ARGS=!ARGS! %1"
shift
goto :collect

:do
if "!ARGS!"=="" (
  echo [INFO] Opening NCM2MP3 GUI...
  "%JAVA%" -jar "%JAR%"
) else (
  echo [INFO] Running: java -jar NCM2MP3-3.1.0.jar!ARGS!
  "%JAVA%" -jar "%JAR%" !ARGS!
)
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo [ERROR] Program exited with code %RC%.
  pause
)
endlocal
