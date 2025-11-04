@echo off
chcp 65001 >nul
echo ========================================
echo Экспорт ключа для RuStore
echo ========================================
echo.

REM Правильный путь к keystore (НАЙДЕН!)
set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks

REM Выходная папка
set OUTPUT_DIR=C:\Рабочая\Android\TAOnline
set OUTPUT_FILE=%OUTPUT_DIR%\pepk_out.zip

REM Ключ шифрования
set ENCRYPTION_KEY=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f

echo ========================================
echo Проверка файлов
echo ========================================
echo.

REM Проверка keystore
if exist "%KEYSTORE_PATH%" (
    echo ✅ Keystore найден: %KEYSTORE_PATH%
    echo    Размер:
    dir "%KEYSTORE_PATH%" | findstr /C:"taonline-release-key.jks"
) else (
    echo ❌ Keystore НЕ найден: %KEYSTORE_PATH%
    echo.
    echo Поиск файлов .jks и .keystore...
    dir /s C:\Users\count\AndroidStudioProjects\TAOnline\*.jks 2>nul
    dir /s C:\Users\count\AndroidStudioProjects\TAOnline\*.keystore 2>nul
    pause
    exit /b 1
)

echo.

REM Проверка pepk.jar
if exist "%OUTPUT_DIR%\pepk.jar" (
    set PEPK_PATH=%OUTPUT_DIR%\pepk.jar
    echo ✅ pepk.jar найден: %PEPK_PATH%
) else if exist "pepk.jar" (
    set PEPK_PATH=%CD%\pepk.jar
    echo ✅ pepk.jar найден в текущей папке: %PEPK_PATH%
) else (
    echo ❌ pepk.jar НЕ найден!
    echo.
    echo Скачайте pepk.jar из RuStore и поместите в:
    echo   %OUTPUT_DIR%\pepk.jar
    echo.
    set /p PEPK_PATH="Или укажите полный путь к pepk.jar: "
    if not exist "%PEPK_PATH%" (
        echo ❌ Файл не найден
        pause
        exit /b 1
    )
)

echo.

REM Проверка алиаса
echo ========================================
echo Проверка алиаса
echo ========================================
echo.
echo Попробуем стандартные алиасы:
echo   1. taonline-key
echo   2. taonline-release-key
echo   3. key
echo.
set /p ALIAS="Введите алиас (или нажмите Enter для taonline-key): "
if "%ALIAS%"=="" set ALIAS=taonline-key

echo.
echo Используем алиас: %ALIAS%
echo.

REM Создание выходной папки
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo ========================================
echo Команда экспорта:
echo ========================================
echo.
echo java -jar "%PEPK_PATH%" ^
echo   --keystore "%KEYSTORE_PATH%" ^
echo   --alias %ALIAS% ^
echo   --output "%OUTPUT_FILE%" ^
echo   --encryptionkey=%ENCRYPTION_KEY% ^
echo   --include-cert
echo.
echo ========================================
echo Запуск...
echo ========================================
echo.
echo Введите пароль keystore когда попросит
echo.

java -jar "%PEPK_PATH%" ^
  --keystore "%KEYSTORE_PATH%" ^
  --alias %ALIAS% ^
  --output "%OUTPUT_FILE%" ^
  --encryptionkey=%ENCRYPTION_KEY% ^
  --include-cert

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo ✅ УСПЕХ! ZIP архив создан
    echo ========================================
    echo.
    echo Файл: %OUTPUT_FILE%
    echo.
    echo Размер:
    dir "%OUTPUT_FILE%" | findstr /C:"pepk_out.zip"
    echo.
    echo Следующий шаг: Загрузите файл в RuStore (шаг 3)
    echo.
) else (
    echo.
    echo ========================================
    echo ❌ ОШИБКА
    echo ========================================
    echo.
    echo Возможные причины:
    echo 1. Неправильный алиас - попробуйте другой
    echo 2. Неправильный пароль keystore
    echo 3. Файл keystore поврежден
    echo.
    echo Попробуйте проверить алиас командой:
    echo   keytool -list -keystore "%KEYSTORE_PATH%"
    echo.
)

pause


