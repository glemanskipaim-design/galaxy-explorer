@echo off
rem Galaxy Explorer (beta) - sobe a aplicacao com Docker e abre o navegador.
rem Na primeira execucao ha build das imagens e carga inicial de dados reais
rem (SIMBAD + imagens), que leva ~20-25 min e exige internet.
setlocal
title Galaxy Explorer
cd /d "%~dp0"

rem Porta publicada: GALAXY_HTTP_PORT do .env, senao 8088 (mesmo default do compose).
set "PORT=8088"
if exist ".env" for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
  if /i "%%A"=="GALAXY_HTTP_PORT" if not "%%B"=="" set "PORT=%%B"
)
set "URL=http://127.0.0.1:%PORT%/"
set "DOCKER_DESKTOP=C:\Program Files\Docker\Docker\Docker Desktop.exe"

where docker >nul 2>&1
if errorlevel 1 (
  echo O comando "docker" nao foi encontrado. Instale o Docker Desktop.
  pause
  exit /b 1
)

docker info >nul 2>&1
if not errorlevel 1 goto dockerok
if not exist "%DOCKER_DESKTOP%" (
  echo Docker parado e Docker Desktop nao encontrado em:
  echo   %DOCKER_DESKTOP%
  echo Inicie o Docker manualmente e rode este atalho de novo.
  pause
  exit /b 1
)
echo Iniciando o Docker Desktop...
start "" "%DOCKER_DESKTOP%"
rem Ate ~3 minutos (60 x 3 s).
set /a TRIES=0
:waitdocker
timeout /t 3 /nobreak >nul
docker info >nul 2>&1
if not errorlevel 1 goto dockerok
set /a TRIES+=1
if %TRIES% geq 60 (
  echo O Docker nao respondeu em 3 minutos. Verifique o Docker Desktop.
  pause
  exit /b 1
)
goto waitdocker
:dockerok

echo Subindo o Galaxy Explorer...
docker compose up -d --build
if errorlevel 1 (
  echo.
  echo Falha ao subir os containers. Veja a mensagem acima.
  pause
  exit /b 1
)

echo Aguardando a aplicacao responder em %URL% ...
rem Ate ~5 minutos (100 x 3 s) - o build ja terminou; aqui so espera ficar healthy.
set /a TRIES=0
:waitapp
curl.exe -s -o nul -f "%URL%" >nul 2>&1
if not errorlevel 1 goto appok
set /a TRIES+=1
if %TRIES% geq 100 (
  echo.
  echo A aplicacao nao respondeu em 5 minutos. Diagnostico:
  echo   docker compose ps
  echo   docker compose logs backend frontend
  pause
  exit /b 1
)
timeout /t 3 /nobreak >nul
goto waitapp
:appok

start "" "%URL%"
echo.
echo Galaxy Explorer no ar em %URL%
echo Primeira execucao? A carga de dados continua em segundo plano:
echo   docker compose logs -f seed
echo Para parar: "Parar Galaxy Explorer.cmd"
timeout /t 10 >nul
