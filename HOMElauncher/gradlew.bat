@echo off
set VERSION=9.6.0
set BASE=%USERPROFILE%\.gradle\wrapper\dists\gradle-%VERSION%-bin\home-launcher
set GRADLE=%BASE%\gradle-%VERSION%\bin\gradle.bat
if not exist "%GRADLE%" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -Command "Invoke-WebRequest https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip -OutFile '%BASE%\gradle.zip'; Expand-Archive -Force '%BASE%\gradle.zip' '%BASE%'"
)
call "%GRADLE%" %*
