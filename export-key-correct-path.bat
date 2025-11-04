@echo off
chcp 65001 >nul
echo ========================================
echo Экспорт ключа для Google Play
echo ========================================
echo.

REM Правильный путь к keystore (найден автоматически)
set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks

REM Выходная папка (создастся автоматически)
set OUTPUT_DIR=C:\Рабочая\Android\TAOnline
set OUTPUT_FILE=%OUTPUT_DIR%\pepk_out.zip

REM Ключ шифрования из Google Play Console
set ENCRYPTION_KEY=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f

echo ✅ Keystore найден: %KEYSTORE_PATH%
echo.

REM Проверка файла
if not exist "%KEYSTORE_PATH%" (
    echo ❌ Файл keystore не найден!
    pause
    exit /b 1
)

echo ========================================
echo ВАЖНО: Нужно узнать правильный алиас!
echo ========================================
echo.
echo Запустите сначала check-my-keystore.bat
echo чтобы узнать правильный алиас.
echo.
set /p ALIAS="Введите алиас из keystore (например: taonline-key или taonline-release-key): "

if "%ALIAS%"=="" (
    echo ❌ Алиас не может быть пустым!
    pause
    exit /b 1
)

echo.
echo Проверка pepk.jar...
if not exist "pepk.jar" (
    echo ❌ Файл pepk.jar не найден в текущей директории!
    echo.
    echo Текущая директория: %CD%
    echo.
    echo Скачайте pepk.jar и поместите в эту папку
    echo или укажите полный путь к pepk.jar
    echo.
    set /p PEPK_PATH="Введите полный путь к pepk.jar (или нажмите Enter для отмены): "
    if "%PEPK_PATH%"=="" (
        exit /b 1
    )
    set PEPK_CMD="%PEPK_PATH%"
) else (
    set PEPK_CMD=pepk.jar
    echo ✅ pepk.jar найден
)

echo.
echo Создание выходной папки...
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo.
echo ========================================
echo Настройки экспорта:
echo ========================================
echo Keystore: %KEYSTORE_PATH%
echo Алиас: %ALIAS%
echo Выходной файл: %OUTPUT_FILE%
echo.
set /p confirm="Все правильно? (y/n): "
if /i not "%confirm%"=="y" (
    echo Отмена
    pause
    exit /b 1
)

echo.
echo ========================================
echo Запуск экспорта...
echo Введите пароль keystore когда попросит
echo ========================================
echo.

java -jar %PEPK_CMD% ^
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
    echo ========================================
    echo Следующие шаги:
    echo ========================================
    echo 1. Загрузите файл pepk_out.zip в Google Play Console (шаг 3)
    echo 2. Затем загрузите сертификат PEM (шаг 4)
    echo.
    echo Файл находится здесь:
    echo %OUTPUT_FILE%
    echo.
) else (
    echo.
    echo ========================================
    echo ❌ ОШИБКА при экспорте
    echo ========================================
    echo.
    echo Возможные причины:
    echo 1. Неправильный алиас - проверьте через check-my-keystore.bat
    echo 2. Неправильный пароль keystore
    echo 3. Файл keystore поврежден
    echo.
    echo Попробуйте:
    echo 1. Запустите check-my-keystore.bat для проверки алиаса
    echo 2. Убедитесь, что вводите правильный пароль
    echo.
)

pause


