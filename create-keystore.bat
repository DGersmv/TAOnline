@echo off
chcp 65001 >nul
echo ========================================
echo Создание Keystore для подписи приложения
echo ========================================
echo.

REM Поиск keytool
set KEYTOOL_CMD=
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\keytool.exe" (
        set KEYTOOL_CMD=%JAVA_HOME%\bin\keytool.exe
    )
)

if not defined KEYTOOL_CMD (
    REM Попробуем найти через where
    where keytool.exe >nul 2>&1
    if %errorlevel% equ 0 (
        for /f "delims=" %%i in ('where keytool.exe') do set KEYTOOL_CMD=%%i
    )
)

if not defined KEYTOOL_CMD (
    REM Попробуем стандартные пути Android Studio
    if exist "%LOCALAPPDATA%\Android\Sdk\jbr\bin\keytool.exe" (
        set "KEYTOOL_CMD=%LOCALAPPDATA%\Android\Sdk\jbr\bin\keytool.exe"
    )
)
if not defined KEYTOOL_CMD (
    if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe" (
        set "KEYTOOL_CMD=%ProgramFiles%\Android\Android Studio\jbr\bin\keytool.exe"
    )
)
if not defined KEYTOOL_CMD (
    if exist "%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe" (
        set "KEYTOOL_CMD=%ProgramFiles(x86)%\Android\Android Studio\jbr\bin\keytool.exe"
    )
)

if not defined KEYTOOL_CMD (
    echo ❌ ОШИБКА: keytool не найден!
    echo.
    echo Пожалуйста, выполните одно из следующих действий:
    echo 1. Установите JDK и добавьте его в PATH
    echo 2. Установите переменную окружения JAVA_HOME
    echo 3. Используйте Android Studio: File ^> Project Structure ^> SDK Location ^> JDK Location
    echo.
    echo Или найдите keytool.exe вручную и запустите команду:
    echo keytool -genkey -v -keystore taonline-release-key.jks ^
    echo   -keyalg RSA -keysize 2048 -validity 10000 -alias taonline-key ^
    echo   -storepass ПАРОЛЬ_ХРАНИЛИЩА -keypass ПАРОЛЬ_КЛЮЧА
    echo.
    pause
    exit /b 1
)

echo ✅ Найден keytool: %KEYTOOL_CMD%
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

echo Введите данные для создания keystore:
echo.

REM Запрашиваем пароли
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

