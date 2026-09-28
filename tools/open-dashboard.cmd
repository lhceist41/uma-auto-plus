@echo off
rem Opens the UMA Auto+ dashboard for MuMu. Double-click this file. Extra arguments go to open-dashboard.ps1.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0open-dashboard.ps1" %*
if errorlevel 1 pause
