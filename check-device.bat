@echo off
chcp 65001 >nul
echo ============================================
echo   Проверка подключения Android устройства
echo ============================================
echo.

set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe

if not exist "%ADB_PATH%" (
    echo [ОШИБКА] ADB не найден!
    echo Проверьте установку Android SDK.
    pause
    exit /b 1
)

echo [1/5] Перезапуск ADB сервера...
"%ADB_PATH%" kill-server >nul 2>&1
timeout /t 2 /nobreak >nul
"%ADB_PATH%" start-server
timeout /t 2 /nobreak >nul

echo [2/5] Проверка подключенных устройств...
echo.
"%ADB_PATH%" devices -l
echo.

echo [3/5] Анализ устройств...
echo.
for /f "tokens=1,2" %%a in ('"%ADB_PATH%" devices ^| findstr /i "device$"') do (
    echo %%a | findstr /i "emulator" >nul
    if errorlevel 1 (
        echo [НАЙДЕНО] Реальное устройство: %%a
    ) else (
        echo [НАЙДЕНО] Эмулятор: %%a
    )
)
echo.

echo [4/5] Проверка USB устройств в Windows...
echo.
powershell -Command "Get-PnpDevice | Where-Object { $_.Status -eq 'OK' -and ($_.FriendlyName -like '*Android*' -or $_.FriendlyName -like '*ADB*' -or $_.FriendlyName -like '*Composite*') } | Select-Object FriendlyName, Status, Class | Format-Table -AutoSize"
echo.

echo [5/5] Проверка неавторизованных устройств...
echo.
"%ADB_PATH%" devices | findstr /i "unauthorized"
if errorlevel 1 (
    echo Нет неавторизованных устройств.
) else (
    echo [ВНИМАНИЕ] Найдены неавторизованные устройства!
    echo На телефоне появится запрос - разрешите отладку!
)
echo.

echo.
echo ============================================
echo   ИНСТРУКЦИЯ ПО НАСТРОЙКЕ USB ОТЛАДКИ:
echo ============================================
echo.
echo 1. На телефоне откройте: Настройки ^> О телефоне
echo 2. Найдите "Номер сборки" и нажмите на него 7 раз
echo 3. Вернитесь в Настройки ^> Для разработчиков
echo 4. Включите "Отладка по USB" (USB debugging)
echo 5. Подключите телефон к компьютеру через USB
echo 6. На телефоне появится запрос "Разрешить отладку по USB?"
echo 7. Поставьте галочку "Всегда разрешать" и нажмите "Разрешить"
echo 8. Выберите режим USB: "Передача файлов" (MTP) или "Передача фото" (PTP)
echo.
echo После этого запустите этот скрипт снова для проверки.
echo.
pause

