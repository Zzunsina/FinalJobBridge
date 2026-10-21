@echo off
rem Delegate to the real project-local npm CLI.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\run-npm.ps1" %*
exit /b %errorlevel%
