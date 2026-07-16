@echo off
setlocal
pushd "%~dp0"
where py.exe >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  py -3 -m frctl_node %*
) else (
  python -m frctl_node %*
)
set "FRCTL_EXIT=%ERRORLEVEL%"
popd
exit /b %FRCTL_EXIT%

