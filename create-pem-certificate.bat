@echo off
chcp 65001 >nul
echo ========================================
echo Создание PEM сертификата для RuStore
echo ========================================
echo.

REM Путь к keystore
set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks

REM Выходной файл
set OUTPUT_DIR=C:\Рабочая\Android\TAOnline
set OUTPUT_PEM=%OUTPUT_DIR%\upload_certificate.pem

echo Проверка keystore...
if not exist "%KEYSTORE_PATH%" (
    echo ❌ Keystore не найден: %KEYSTORE_PATH%
    pause
    exit /b 1
)

echo ✅ Keystore найден: %KEYSTORE_PATH%
echo.

REM Поиск keytool
set KEYTOOL_PATH=
if exist "%LOCALAPPDATA%\Android\Sdk\jbr\bin\keytool.exe" (
    set KEYTOOL_PATH=%LOCALAPPDATA%\Android\Sdk\jbr\bin\keytool.exe
) else if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe" (
    set KEYTOOL_PATH=%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe
) else if exist "%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe" (
    set KEYTOOL_PATH=%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe
)

if "%KEYTOOL_PATH%"=="" (
    echo ❌ keytool не найден автоматически
    echo.
    set /p KEYTOOL_PATH="Введите полный путь к keytool.exe: "
    if not exist "%KEYTOOL_PATH%" (
        echo ❌ Файл не найден
        pause
        exit /b 1
    )
)

echo ✅ keytool найден: %KEYTOOL_PATH%
echo.

REM Проверка алиаса
echo ========================================
echo Проверка алиаса
echo ========================================
echo.
echo Список алиасов (введите пароль):
echo.
"%KEYTOOL_PATH%" -list -keystore "%KEYSTORE_PATH%"
echo.

set /p ALIAS="Введите алиас из списка выше (или Enter для taonline-key): "
if "%ALIAS%"=="" set ALIAS=taonline-key

echo.
echo Используем алиас: %ALIAS%
echo.

REM Создание выходной папки
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo ========================================
echo Создание PEM сертификата...
echo ========================================
echo.
echo Команда:
echo   keytool -export -rfc -keystore "%KEYSTORE_PATH%" -alias %ALIAS% -file "%OUTPUT_PEM%"
echo.
echo Введите пароль keystore когда попросит
echo.

"%KEYTOOL_PATH%" -export -rfc ^
  -keystore "%KEYSTORE_PATH%" ^
  -alias %ALIAS% ^
  -file "%OUTPUT_PEM%"

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo ✅ УСПЕХ! PEM сертификат создан
    echo ========================================
    echo.
    echo Файл: %OUTPUT_PEM%
    echo.
    echo Размер:
    dir "%OUTPUT_PEM%" | findstr /C:"upload_certificate.pem"
    echo.
    echo ========================================
    echo Следующий шаг:
    echo ========================================
    echo 1. Загрузите файл upload_certificate.pem в RuStore (шаг 4)
    echo 2. Нажмите "Отправить подпись"
    echo.
    echo Файл находится здесь:
    echo %OUTPUT_PEM%
    echo.
) else (
    echo.
    echo ========================================
    echo ❌ ОШИБКА при создании PEM
    echo ========================================
    echo.
    echo Возможные причины:
    echo 1. Неправильный алиас - проверьте список выше
    echo 2. Неправильный пароль keystore
    echo 3. Файл keystore поврежден
    echo.
    echo Попробуйте еще раз с правильным алиасом
    echo.
)

pause


