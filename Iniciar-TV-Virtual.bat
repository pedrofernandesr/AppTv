@echo off
title CineTV - Simulador Android TV
echo ========================================================
echo         INICIANDO CINETV - SIMULADOR ANDROID TV
echo ========================================================
echo.
echo Use as setas do teclado para navegar:
echo - [CIMA / BAIXO / ESQUERDA / DIREITA]
echo - [ENTER] para selecionar / assistir
echo - [ESC] para voltar
echo.
start "" "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd AndroidTV -gpu host
exit
