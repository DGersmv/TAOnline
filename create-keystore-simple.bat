@echo off
chcp 65001 >nul
echo ========================================
echo Создание Keystore для подписи приложения
echo ========================================
echo.

REM Проверяем, существует ли уже keystore
if exist "taonline-release-key.jks" (
    echo ⚠️  Файл taonline-release-key.jks уже существует!
    echo.
    set /p overwrite="Перезаписать существующий keystore? (y/n): "
    if /i not "%overwrite%"=="y" (
        echo Операция отменена.
        pause
        exit /b
    )
    del "taonline-release-key.jks"
)

echo.
echo ⚠️  ВНИМАНИЕ: Для создания keystore нужен keytool из JDK
echo.
echo Варианты:
echo 1. Используйте Android Studio: Build ^> Generate Signed Bundle / APK
echo 2. Найдите keytool.exe в папке JDK (обычно в Android Studio\jbr\bin\)
echo 3. Или используйте команду вручную:
echo.
echo    keytool -genkey -v -keystore taonline-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias taonline-key -storepass ПАРОЛЬ1 -keypass ПАРОЛЬ2
echo.
echo.
set /p continue="Продолжить с поиском keytool? (y/n): "
if /i not "%continue%"=="y" (
    echo Операция отменена.
    pause
    exit /b
)

echo.
echo Поиск keytool...

REM Попробуем найти через where
where keytool.exe >nul 2>&1
if %errorlevel% equ 0 (
    for /f "delims=" %%i in ('where keytool.exe') do set KEYTOOL_CMD=%%i
    goto found_keytool
)

REM Попробуем JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\keytool.exe" (
        set "KEYTOOL_CMD=%JAVA_HOME%\bin\keytool.exe"
        goto found_keytool
    )
)

REM Попробуем стандартные пути
set "TEST_PATHS[0]=%LOCALAPPDATA%\Android\Sdk\jbr\bin\keytool.exe"
set "TEST_PATHS[1]=%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe"
set "TEST_PATHS[2]=%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe"
set "TEST_PATHS[3]=%USERPROFILE%\AppData\Local\Android\Sdk\jbr\bin\keytool.exe"

for /L %%i in (0,1,3) do (
    call set "TEST_PATH=%%TEST_PATHS[%%i]%%"
    if exist "!TEST_PATH!" (
        set "KEYTOOL_CMD=!TEST_PATH!"
        goto found_keytool
    )
)

echo ❌ keytool не найден автоматически.
echo.
echo Пожалуйста, укажите полный путь к keytool.exe:
set /p KEYTOOL_CMD="Путь к keytool.exe: "
if not exist "%KEYTOOL_CMD%" (
    echo ❌ Файл не найден: %KEYTOOL_CMD%
    pause
    exit /b 1
)

:found_keytool
echo ✅ Найден keytool: %KEYTOOL_CMD%
echo.

echo Введите данные для создания keystore:
echo.

set /p keystore_password="Пароль хранилища (store password): "
set /p key_password="Пароль ключа (key password): "

echo.
echo Введите данные для сертификата:
echo.

set /p name="Ваше имя/название: "
set /p org="Организация: "
set /p city="Город: "
set /p state="Регион/Область: "
set /p country="Страна (2 буквы, например RU): "

echo.
echo Создание keystore...

"%KEYTOOL_CMD%" -genkey -v -keystore taonline-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias taonline-key -storepass "%keystore_password%" -keypass "%key_password%" -dname "CN=%name%, OU=%org%, O=%org%, L=%city%, ST=%state%, C=%country%"

if %errorlevel% equ 0 (
    echo.
    echo ✅ Keystore успешно создан: taonline-release-key.jks
    echo.
    
    REM Создаем файл keystore.properties
    (
        echo # Keystore пароли для подписи приложения
        echo # НЕ коммитьте этот файл в Git!
        echo KEYSTORE_PASSWORD=%keystore_password%
        echo KEY_PASSWORD=%key_password%
    ) > keystore.properties
    
    echo ✅ Файл keystore.properties создан
    echo.
    echo ⚠️  ВАЖНО: Сохраните эти данные в безопасном месте:
    echo    - Файл: taonline-release-key.jks
    echo    - Пароль хранилища: %keystore_password%
    echo    - Пароль ключа: %key_password%
    echo    - Алиас: taonline-key
    echo.
    echo Без этих данных вы не сможете обновлять приложение!
) else (
    echo.
    echo ❌ Ошибка при создании keystore
)

pause


