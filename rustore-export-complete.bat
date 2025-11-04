@echo off
chcp 65001 >nul
echo ========================================
echo Экспорт ключа для RuStore
echo ========================================
echo.

set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks
set OUTPUT_DIR=C:\Рабочая\Android\TAOnline
set OUTPUT_ZIP=%OUTPUT_DIR%\pepk_out.zip
set OUTPUT_PEM=%OUTPUT_DIR%\upload_certificate.pem

REM Ключ шифрования из RuStore (замените на ваш, если другой)
set ENCRYPTION_KEY=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f

echo ========================================
echo Шаг 1: Проверка keystore
echo ========================================
echo.

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
echo Список алиасов в keystore:
echo (Введите пароль когда попросит)
echo ========================================
echo.
"%KEYTOOL_PATH%" -list -keystore "%KEYSTORE_PATH%"
echo.

set /p ALIAS="Введите алиас из списка выше: "

if "%ALIAS%"=="" (
    echo ❌ Алиас не может быть пустым!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Шаг 2: Проверка pepk.jar
echo ========================================
echo.

if not exist "pepk.jar" (
    echo ❌ pepk.jar не найден в текущей директории!
    echo Текущая директория: %CD%
    echo.
    echo Скачайте pepk.jar из RuStore и поместите в эту папку
    echo Или укажите полный путь к pepk.jar
    echo.
    set /p PEPK_PATH="Введите полный путь к pepk.jar: "
    if not exist "%PEPK_PATH%" (
        echo ❌ Файл не найден
        pause
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
echo Шаг 3: Экспорт ключа (ZIP архив)
echo ========================================
echo.
echo Настройки:
echo   Keystore: %KEYSTORE_PATH%
echo   Алиас: %ALIAS%
echo   Выходной файл: %OUTPUT_ZIP%
echo.
echo Введите пароль keystore когда попросит
echo.

java -jar %PEPK_CMD% ^
  --keystore "%KEYSTORE_PATH%" ^
  --alias %ALIAS% ^
  --output "%OUTPUT_ZIP%" ^
  --encryptionkey=%ENCRYPTION_KEY% ^
  --include-cert

if %errorlevel% neq 0 (
    echo.
    echo ❌ Ошибка при экспорте ZIP
    echo Проверьте правильность алиаса и пароля
    pause
    exit /b 1
)

echo.
echo ✅ ZIP архив создан: %OUTPUT_ZIP%
echo.

echo ========================================
echo Шаг 4: Экспорт сертификата (PEM)
echo ========================================
echo.
echo Введите пароль keystore еще раз
echo.

"%KEYTOOL_PATH%" -export -rfc ^
  -keystore "%KEYSTORE_PATH%" ^
  -alias %ALIAS% ^
  -file "%OUTPUT_PEM%"

if %errorlevel% neq 0 (
    echo.
    echo ❌ Ошибка при экспорте PEM
    pause
    exit /b 1
)

echo.
echo ✅ PEM сертификат создан: %OUTPUT_PEM%
echo.

echo ========================================
echo ✅ ВСЕ ГОТОВО!
echo ========================================
echo.
echo Файлы для загрузки в RuStore:
echo.
echo 1. ZIP архив (шаг 3):
echo    %OUTPUT_ZIP%
echo    Размер:
dir "%OUTPUT_ZIP%" | findstr /C:"pepk_out.zip"
echo.
echo 2. PEM сертификат (шаг 4):
echo    %OUTPUT_PEM%
echo    Размер:
dir "%OUTPUT_PEM%" | findstr /C:"upload_certificate.pem"
echo.
echo ========================================
echo Следующие шаги:
echo ========================================
echo 1. Загрузите pepk_out.zip в RuStore (шаг 3)
echo 2. Загрузите upload_certificate.pem в RuStore (шаг 4)
echo 3. Нажмите "Отправить подпись"
echo.
pause


