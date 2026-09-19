@echo off
cd /d "%~dp0"
py scripts\lite.py start %*
exit /b %errorlevel%
