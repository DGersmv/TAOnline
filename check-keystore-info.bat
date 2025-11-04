@echo off
chcp 65001 >nul
echo ========================================
echo Проверка информации о keystore
echo ========================================
echo.

REM Проверяем разные возможные пути
echo Поиск keystore файлов...
echo.

set FOUND=0

REM Путь 1
if exist "C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks" (
    echo ✅ Найден: C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks
    set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks
    set FOUND=1
)

REM Путь 2
if exist "C:\Рабочая\Android\taonline-release-key.jks" (
    echo ✅ Найден: C:\Рабочая\Android\taonline-release-key.jks
    if %FOUND%==0 (
        set KEYSTORE_PATH=C:\Рабочая\Android\taonline-release-key.jks
        set FOUND=1
    )
)

REM Путь 3 - в текущей директории
if exist "taonline-release-key.jks" (
    echo ✅ Найден: %CD%\taonline-release-key.jks
    if %FOUND%==0 (
        set KEYSTORE_PATH=%CD%\taonline-release-key.jks
        set FOUND=1
    )
)

if %FOUND%==0 (
    echo ❌ Keystore файл не найден!
    echo.
    echo Поиск всех .jks файлов...
    dir /s C:\Users\count\AndroidStudioProjects\TAOnline\*.jks 2>nul
    dir /s C:\Рабочая\Android\*.jks 2>nul
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo Информация о keystore
echo ========================================
echo.
echo Путь: %KEYSTORE_PATH%
echo.
echo Список алиасов в keystore:
echo (Введите пароль когда попросит)
echo.
keytool -list -v -keystore "%KEYSTORE_PATH%"

echo.
echo ========================================
echo Выберите правильный алиас из списка выше
echo ========================================
pause


