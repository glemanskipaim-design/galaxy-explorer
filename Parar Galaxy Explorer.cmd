@echo off
rem Para os containers do Galaxy Explorer (os dados ficam preservados nos volumes).
setlocal
cd /d "%~dp0"
docker compose stop
if errorlevel 1 (
  echo.
  echo Nao foi possivel parar os containers. Veja a mensagem acima.
  pause
  exit /b 1
)
echo.
echo Galaxy Explorer parado. Os dados foram preservados.
timeout /t 5 >nul
