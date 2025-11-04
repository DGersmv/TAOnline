@echo off
chcp 65001 >nul
echo ========================================
echo Поиск keytool и проверка keystore
echo ========================================
echo.

set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks

echo Поиск keytool...
echo.

REM Поиск keytool в стандартных местах
set KEYTOOL_PATH=

REM Проверка JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\keytool.exe" (
        set KEYTOOL_PATH=%JAVA_HOME%\bin\keytool.exe
        echo ✅ Найден через JAVA_HOME: %KEYTOOL_PATH%
        goto :found
    )
)

REM Проверка Android Studio пути
if exist "%LOCALAPPDATA%\Android\Sdk\jbr\bin\keytool.exe" (
    set KEYTOOL_PATH=%LOCALAPPDATA%\Android\Sdk\jbr\bin\keytool.exe
    echo ✅ Найден в Android SDK: %KEYTOOL_PATH%
    goto :found
)

REM Проверка Program Files
if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe" (
    set KEYTOOL_PATH=%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe
    echo ✅ Найден в Android Studio: %KEYTOOL_PATH%
    goto :found
)

REM Проверка Program Files (x86)
if exist "%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe" (
    set KEYTOOL_PATH=%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe
    echo ✅ Найден в Android Studio (x86): %KEYTOOL_PATH%
    goto :found
)

REM Проверка через where
where keytool.exe >nul 2>&1
if %errorlevel% equ 0 (
    for /f "delims=" %%i in ('where keytool.exe') do (
        set KEYTOOL_PATH=%%i
        echo ✅ Найден в PATH: %KEYTOOL_PATH%
        goto :found
    )
)

REM Если не найден, попросим указать путь
echo ❌ keytool не найден автоматически
echo.
echo Пожалуйста, укажите путь к keytool.exe вручную
echo Обычно он находится в:
echo   - Android Studio\jbr\bin\keytool.exe
echo   - JDK\bin\keytool.exe
echo.
set /p KEYTOOL_PATH="Введите полный путь к keytool.exe: "

if not exist "%KEYTOOL_PATH%" (
    echo ❌ Файл не найден: %KEYTOOL_PATH%
    pause
    exit /b 1
)

:found
echo.
echo ========================================
echo Проверка keystore
echo ========================================
echo.
echo Keystore: %KEYSTORE_PATH%
echo.
echo Список алиасов в keystore:
echo (Введите пароль keystore когда попросит)
echo.
"%KEYTOOL_PATH%" -list -v -keystore "%KEYSTORE_PATH%"

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo ✅ Проверка завершена!
    echo ========================================
    echo.
    echo Скопируйте алиас из списка выше (обычно это первое имя после "Alias name:")
    echo.
) else (
    echo.
    echo ❌ Ошибка при проверке keystore
    echo Возможно, неправильный пароль
)

pause


