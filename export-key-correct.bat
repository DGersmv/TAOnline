@echo off
chcp 65001 >nul
echo ========================================
echo Экспорт ключа для Google Play Console
echo ========================================
echo.

REM Путь к keystore (измените если нужно)
set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks
set ALIAS=taonline-key
set OUTPUT_FILE=C:\Рабочая\Android\TAOnline\pepk_out.zip
set ENCRYPTION_KEY=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f

REM Проверка существования keystore
if not exist "%KEYSTORE_PATH%" (
    echo ❌ Keystore не найден: %KEYSTORE_PATH%
    echo.
    echo Пожалуйста, укажите правильный путь к keystore файлу.
    echo.
    pause
    exit /b 1
)

REM Проверка существования pepk.jar
if not exist "pepk.jar" (
    echo ❌ Файл pepk.jar не найден в текущей директории!
    echo.
    echo Скачайте pepk.jar с:
    echo https://github.com/google/play-emm-server/tree/master/pepk
    echo.
    pause
    exit /b 1
)

echo ✅ Keystore найден: %KEYSTORE_PATH%
echo ✅ Алиас: %ALIAS%
echo ✅ Выходной файл: %OUTPUT_FILE%
echo.
echo Запуск экспорта...
echo.

java -jar pepk.jar ^
  --keystore "%KEYSTORE_PATH%" ^
  --alias %ALIAS% ^
  --output "%OUTPUT_FILE%" ^
  --encryptionkey=%ENCRYPTION_KEY% ^
  --include-cert

if %errorlevel% equ 0 (
    echo.
    echo ✅ Экспорт успешно завершен!
    echo ✅ Файл создан: %OUTPUT_FILE%
    echo.
    echo Следующий шаг: Загрузите pepk_out.zip в Google Play Console
) else (
    echo.
    echo ❌ Ошибка при экспорте
    echo.
    echo Проверьте:
    echo 1. Правильность пути к keystore
    echo 2. Правильность алиаса
    echo 3. Правильность пароля
    echo 4. Наличие pepk.jar в текущей директории
)

pause


