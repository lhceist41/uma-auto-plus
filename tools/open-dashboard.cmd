@echo off
rem Opens the UMA Auto+ dashboard for MuMu. Double-click this file. Extra arguments go to open-dashboard.ps1.
if not exist "%~dp0open-dashboard.ps1" (
    echo open-dashboard.ps1 is not next to this file. Extract the whole zip first: right-click it, then Extract All.
    pause
    exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0open-dashboard.ps1" %*
if errorlevel 1 pause
