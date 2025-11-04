@echo off
chcp 65001 >nul
echo ========================================
echo Исправление команды экспорта для RuStore
echo ========================================
echo.

REM Правильный путь к keystore
set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks

REM Выходная папка
set OUTPUT_DIR=C:\Рабочая\Android\TAOnline
set OUTPUT_FILE=%OUTPUT_DIR%\pepk_out.zip

REM Ключ шифрования
set ENCRYPTION_KEY=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f

echo Проверка файлов...
echo.

REM Проверка keystore
if not exist "%KEYSTORE_PATH%" (
    echo ❌ Keystore не найден: %KEYSTORE_PATH%
    echo.
    echo Поиск keystore...
    dir /s C:\Users\count\AndroidStudioProjects\TAOnline\*.jks 2>nul
    pause
    exit /b 1
)
echo ✅ Keystore найден: %KEYSTORE_PATH%
echo.

REM Поиск pepk.jar
set PEPK_PATH=

REM Проверка в текущей директории
if exist "pepk.jar" (
    set PEPK_PATH=%CD%\pepk.jar
    echo ✅ pepk.jar найден в текущей директории: %PEPK_PATH%
    goto :found_pepk
)

REM Проверка в выходной папке
if exist "%OUTPUT_DIR%\pepk.jar" (
    set PEPK_PATH=%OUTPUT_DIR%\pepk.jar
    echo ✅ pepk.jar найден: %PEPK_PATH%
    goto :found_pepk
)

REM Проверка в Рабочая\Android
if exist "C:\Рабочая\Android\pepk.jar" (
    set PEPK_PATH=C:\Рабочая\Android\pepk.jar
    echo ✅ pepk.jar найден: %PEPK_PATH%
    goto :found_pepk
)

REM Если не найден
echo ❌ pepk.jar не найден!
echo.
echo Где искать pepk.jar:
echo 1. Скачайте из RuStore (шаг 1 - кнопка "Скачать")
echo 2. Поместите файл в одну из папок:
echo    - %CD%\pepk.jar (текущая папка)
echo    - %OUTPUT_DIR%\pepk.jar
echo    - C:\Рабочая\Android\pepk.jar
echo.
set /p PEPK_PATH="Или укажите полный путь к pepk.jar: "
if not exist "%PEPK_PATH%" (
    echo ❌ Файл не найден: %PEPK_PATH%
    pause
    exit /b 1
)

:found_pepk
echo ✅ pepk.jar: %PEPK_PATH%
echo.

REM Проверка алиаса
echo ========================================
echo Нужно узнать правильный алиас
echo ========================================
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
    echo ⚠️  keytool не найден автоматически
    echo.
    echo Список алиасов (введите пароль):
    echo.
    set /p KEYTOOL_PATH="Введите полный путь к keytool.exe: "
    if not exist "%KEYTOOL_PATH%" (
        echo Используем стандартные алиасы...
        set ALIAS=taonline-key
    ) else (
        "%KEYTOOL_PATH%" -list -keystore "%KEYSTORE_PATH%"
        echo.
        set /p ALIAS="Введите алиас из списка выше: "
    )
) else (
    echo Список алиасов (введите пароль):
    echo.
    "%KEYTOOL_PATH%" -list -keystore "%KEYSTORE_PATH%"
    echo.
    set /p ALIAS="Введите алиас из списка выше: "
)

if "%ALIAS%"=="" (
    echo Используем стандартный алиас: taonline-key
    set ALIAS=taonline-key
)

echo.
echo Создание выходной папки...
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo.
echo ========================================
echo Правильная команда:
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
echo Запуск команды...
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
    dir "%OUTPUT_FILE%"
    echo.
    echo Следующий шаг: Загрузите файл в RuStore (шаг 3)
    echo.
) else (
    echo.
    echo ❌ Ошибка при экспорте
    echo Проверьте:
    echo 1. Правильность алиаса
    echo 2. Правильность пароля keystore
    echo.
)

pause


