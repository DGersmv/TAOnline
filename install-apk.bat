@echo off
chcp 65001 >nul
echo ============================================
echo   Установка APK на подключенное устройство
echo ============================================
echo.

set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk

if not exist "%ADB_PATH%" (
    echo [ОШИБКА] ADB не найден!
    pause
    exit /b 1
)

if not exist "%APK_PATH%" (
    echo [ОШИБКА] APK не найден!
    echo Сначала соберите проект: gradlew.bat assembleDebug
    pause
    exit /b 1
)

echo Проверка подключенных устройств...
echo.
"%ADB_PATH%" devices -l
echo.

set DEVICE_FOUND=0
set DEVICE_ID=

REM Проверяем наличие реальных устройств (не эмуляторов)
for /f "tokens=1,2" %%a in ('"%ADB_PATH%" devices ^| findstr /i "device$"') do (
    echo %%a | findstr /i "emulator" >nul
    if errorlevel 1 (
        set DEVICE_ID=%%a
        set DEVICE_FOUND=1
        goto :found_device
    )
)

REM Если реальное устройство не найдено, используем эмулятор
for /f "tokens=1,2" %%a in ('"%ADB_PATH%" devices ^| findstr /i "device$"') do (
    set DEVICE_ID=%%a
    set DEVICE_FOUND=1
    goto :found_device
)

:found_device
if %DEVICE_FOUND% EQU 0 (
    echo [ОШИБКА] Устройство не найдено!
    echo.
    echo Убедитесь что:
    echo 1. Телефон подключен по USB
    echo 2. USB-отладка включена на телефоне
    echo 3. На телефоне разрешена отладка для этого компьютера
    echo 4. Выбран режим "Передача файлов" (MTP)
    echo.
    echo Запустите check-device.bat для диагностики
    pause
    exit /b 1
)

echo Установка APK на устройство: %DEVICE_ID%
echo.
"%ADB_PATH%" install -r "%APK_PATH%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo [УСПЕХ] APK успешно установлен!
    echo ========================================
) else (
    echo.
    echo ========================================
    echo [ОШИБКА] Установка не удалась!
    echo ========================================
)

pause
