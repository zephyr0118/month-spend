@echo off
setlocal
cd /d "%~dp0"
set "SCRIPT=%~dp0tools\build-lan.ps1"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:SCRIPT,[ref]$tokens,[ref]$errors) | Out-Null; if($errors.Count -gt 0){ Write-Host 'PowerShell syntax check failed:' -ForegroundColor Red; $errors | ForEach-Object { Write-Host ('Line ' + $_.Extent.StartLineNumber + ': ' + $_.Message) -ForegroundColor Red }; exit 1 }"
if errorlevel 1 (
    echo.
    echo PowerShell syntax check failed. Please update the repository and try again.
    pause
    exit /b 1
)

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo Script failed. Exit code: %EXIT_CODE%
    echo Check the error message above.
    pause
)

exit /b %EXIT_CODE%
