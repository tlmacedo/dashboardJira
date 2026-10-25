@echo off
REM Inicio rapido (Windows): cria o ambiente, instala dependencias e sobe o painel.
REM   start.bat        -> usa o modo definido em .env (sem .env: demonstracao com dados ficticios)
REM   start.bat demo   -> forca a demonstracao
cd /d "%~dp0"
if not exist .venv ( py -3 -m venv .venv )
call .venv\Scripts\activate.bat
pip install -q -r backend\requirements.txt
cd backend
set PYTHONPATH=%CD%
if "%1"=="demo" set JIRADASH_MODE=demo
if not exist ..\.env set JIRADASH_MODE=demo
if "%JIRADASH_MODE%"=="demo" echo ^>^> Modo DEMONSTRACAO (dados ficticios). Para usar o seu Jira: python -m jiradash init
echo ^>^> Abrindo em http://localhost:8000
python -m uvicorn jiradash.main:app --host 0.0.0.0 --port 8000
