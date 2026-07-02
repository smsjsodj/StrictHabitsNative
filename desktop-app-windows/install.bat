@echo off
echo Installing Strict Habits Desktop...

python --version >nul 2>&1
if errorlevel 1 (
    echo Python not found! Please install Python 3.8+ from https://www.python.org/
    pause
    exit /b
)

echo Installing required packages...
pip install PyQt6

echo.
echo Installation complete!
echo.
echo To run the app, double-click "run.bat" or execute: python strict_habits.py
pause
