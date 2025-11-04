@echo off
chcp 65001 >nul
echo ========================================
echo Экспорт ключа для Google Play
echo ========================================
echo.

REM Настройки (ИЗМЕНИТЕ ПРИ НЕОБХОДИМОСТИ)
set KEYSTORE_PATH=C:\Рабочая\Android\taonline-release-key.jks
set ALIAS=taonline-release-key
set OUTPUT_FILE=C:\Рабочая\Android\TAOnline\pepk_out.zip
set ENCRYPTION_KEY=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f

echo Текущие настройки:
echo   Keystore: %KEYSTORE_PATH%
echo   Алиас: %ALIAS%
echo   Выходной файл: %OUTPUT_FILE%
echo.
set /p confirm="Правильно? (y/n): "
if /i not "%confirm%"=="y" (
    echo.
    set /p KEYSTORE_PATH="Введите путь к keystore: "
    set /p ALIAS="Введите алиас: "
    set /p OUTPUT_FILE="Введите путь для выходного файла: "
)

echo.
echo Проверка файла keystore...
if not exist "%KEYSTORE_PATH%" (
    echo ❌ Файл не найден: %KEYSTORE_PATH%
    echo.
    echo Попробуйте запустить check-keystore-info.bat для поиска файла
    pause
    exit /b 1
)

echo ✅ Keystore найден
echo.

REM Проверка pepk.jar
if not exist "pepk.jar" (
    echo ❌ Файл pepk.jar не найден в текущей директории!
    echo Текущая директория: %CD%
    echo.
    echo Скачайте pepk.jar и поместите в эту папку
    pause
    exit /b 1
)

echo ✅ pepk.jar найден
echo.

REM Создаем выходную папку если не существует
for %%F in ("%OUTPUT_FILE%") do set OUTPUT_DIR=%%~dpF
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo.
echo ========================================
echo Запуск экспорта...
echo ========================================
echo.

java -jar pepk.jar ^
  --keystore "%KEYSTORE_PATH%" ^
  --alias %ALIAS% ^
  --output "%OUTPUT_FILE%" ^
  --encryptionkey=%ENCRYPTION_KEY% ^
  --include-cert

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo ✅ УСПЕХ! Экспорт завершен!
    echo ========================================
    echo.
    echo Файл создан: %OUTPUT_FILE%
    echo.
    echo Размер файла:
    dir "%OUTPUT_FILE%"
    echo.
    echo Следующий шаг:
    echo 1. Загрузите файл pepk_out.zip в Google Play Console
    echo 2. Затем загрузите сертификат PEM (шаг 4)
    echo.
) else (
    echo.
    echo ========================================
    echo ❌ ОШИБКА при экспорте
    echo ========================================
    echo.
    echo Возможные причины:
    echo 1. Неправильный путь к keystore
    echo 2. Неправильный алиас
    echo 3. Неправильный пароль
    echo.
    echo Попробуйте:
    echo 1. Запустите check-keystore-info.bat для проверки алиаса
    echo 2. Проверьте правильность пути к keystore
    echo 3. Убедитесь, что вводите правильный пароль
    echo.
)

pause


