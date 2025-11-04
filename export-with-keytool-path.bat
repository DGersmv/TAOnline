@echo off
chcp 65001 >nul
echo ========================================
echo Экспорт ключа для Google Play
echo ========================================
echo.

set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks
set OUTPUT_DIR=C:\Рабочая\Android\TAOnline
set OUTPUT_FILE=%OUTPUT_DIR%\pepk_out.zip
set ENCRYPTION_KEY=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f

echo Поиск keytool...
set KEYTOOL_PATH=

REM Проверка стандартных путей
if exist "%LOCALAPPDATA%\Android\Sdk\jbr\bin\keytool.exe" (
    set KEYTOOL_PATH=%LOCALAPPDATA%\Android\Sdk\jbr\bin\keytool.exe
) else if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe" (
    set KEYTOOL_PATH=%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe
) else if exist "%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe" (
    set KEYTOOL_PATH=%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe
) else if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\keytool.exe" (
        set KEYTOOL_PATH=%JAVA_HOME%\bin\keytool.exe
    )
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

REM Проверка keystore
if not exist "%KEYSTORE_PATH%" (
    echo ❌ Keystore не найден: %KEYSTORE_PATH%
    pause
    exit /b 1
)

echo ✅ Keystore найден: %KEYSTORE_PATH%
echo.

REM Сначала показываем алиасы
echo ========================================
echo Сначала проверьте алиас:
echo ========================================
"%KEYTOOL_PATH%" -list -keystore "%KEYSTORE_PATH%"
echo.

set /p ALIAS="Введите алиас из списка выше: "

if "%ALIAS%"=="" (
    echo ❌ Алиас не может быть пустым!
    pause
    exit /b 1
)

REM Проверка pepk.jar
if not exist "pepk.jar" (
    echo ❌ pepk.jar не найден в текущей директории!
    echo Текущая директория: %CD%
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
echo Настройки:
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
    echo Следующий шаг: Загрузите файл в Google Play Console
    echo.
) else (
    echo.
    echo ❌ Ошибка при экспорте
    echo Проверьте правильность алиаса и пароля
)

pause


