@echo off
chcp 65001 >nul
echo ============================================
echo   ДИАГНОСТИКА ПОДКЛЮЧЕНИЯ ТЕЛЕФОНА
echo ============================================
echo.

set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe

if not exist "%ADB_PATH%" (
    echo [ОШИБКА] ADB не найден!
    pause
    exit /b 1
)

echo [ШАГ 1] Перезапуск ADB сервера...
"%ADB_PATH%" kill-server
timeout /t 2 /nobreak >nul
"%ADB_PATH%" start-server
timeout /t 2 /nobreak >nul
echo Готово!
echo.

echo [ШАГ 2] Список всех устройств (включая неавторизованные):
echo.
"%ADB_PATH%" devices -l
echo.

echo [ШАГ 3] Проверка статуса устройств:
echo.
"%ADB_PATH%" devices | findstr /i "unauthorized" >nul
if %ERRORLEVEL% EQU 0 (
    echo [ПРОБЛЕМА] Найдены неавторизованные устройства!
    echo.
    echo РЕШЕНИЕ:
    echo 1. Посмотрите на экран телефона
    echo 2. Должен появиться запрос "Разрешить отладку по USB?"
    echo 3. Поставьте галочку "Всегда разрешать с этого компьютера"
    echo 4. Нажмите "Разрешить" или "Allow"
    echo 5. Запустите этот скрипт снова
    echo.
) else (
    echo Нет неавторизованных устройств.
    echo.
)

"%ADB_PATH%" devices | findstr /i "offline" >nul
if %ERRORLEVEL% EQU 0 (
    echo [ПРОБЛЕМА] Найдены устройства в статусе "offline"!
    echo.
    echo РЕШЕНИЕ:
    echo 1. Отключите и снова подключите USB кабель
    echo 2. На телефоне выберите режим "Передача файлов" (MTP)
    echo 3. Перезапустите ADB: adb kill-server ^&^& adb start-server
    echo.
)

echo [ШАГ 4] USB устройства в Windows:
echo.
powershell -Command "Get-PnpDevice | Where-Object { $_.Status -eq 'OK' -and ($_.FriendlyName -like '*Android*' -or $_.FriendlyName -like '*ADB*' -or $_.FriendlyName -like '*Composite*' -or $_.Class -eq 'AndroidUsbDeviceClass') } | Select-Object FriendlyName, Status, Class | Format-Table -AutoSize"
echo.

echo [ШАГ 5] Проверка драйверов Android:
echo.
powershell -Command "$devices = Get-PnpDevice | Where-Object { $_.FriendlyName -like '*Android*' -or $_.FriendlyName -like '*ADB*' }; if ($devices) { Write-Host 'Драйверы найдены:' -ForegroundColor Green; $devices | Select-Object FriendlyName, Status | Format-Table } else { Write-Host 'Драйверы Android не найдены!' -ForegroundColor Red; Write-Host 'Возможно нужно установить драйверы производителя телефона' }"
echo.

echo ============================================
echo   ЧЕКЛИСТ ДЛЯ ПОДКЛЮЧЕНИЯ ТЕЛЕФОНА:
echo ============================================
echo.
echo [ ] Режим разработчика включен (7 нажатий на "Номер сборки")
echo [ ] USB-отладка включена в настройках разработчика
echo [ ] Телефон подключен по USB к компьютеру
echo [ ] На телефоне разрешена отладка (появился и подтвержден запрос)
echo [ ] Выбран режим USB: "Передача файлов" (MTP) или "Передача фото" (PTP)
echo [ ] Используется рабочий USB кабель (не только для зарядки)
echo [ ] Попробован другой USB порт (лучше USB 2.0)
echo.
echo ============================================
echo   ЕСЛИ ТЕЛЕФОН ВСЕ ЕЩЕ НЕ ВИДЕН:
echo ============================================
echo.
echo 1. Отключите телефон от USB
echo 2. На телефоне: Настройки ^> Для разработчиков ^> Отключить USB-отладку
echo 3. Подождите 5 секунд
echo 4. Включите USB-отладку снова
echo 5. Подключите телефон к компьютеру
echo 6. На телефоне должно появиться окно с запросом - разрешите!
echo 7. Запустите этот скрипт снова
echo.
pause



