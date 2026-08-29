@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-lan.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo 脚本执行失败，错误码：%EXIT_CODE%
    echo 请查看上方错误信息。
    pause
)

exit /b %EXIT_CODE%
