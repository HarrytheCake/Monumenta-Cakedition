@echo off
rem Double-click to release: pushes the pack (packwiz branch) and the CakeUpdater repo after checks.
cd /d "%~dp0.."
python tools\push_all.py %*
pause
