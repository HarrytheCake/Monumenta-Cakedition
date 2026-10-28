@echo off
rem Double-click to release: pushes the pack (packwiz branch on GitHub + GitLab) and the CakeUpdater + CakeText mirrors after checks.
cd /d "%~dp0.."
python tools\push_all.py %*
pause
