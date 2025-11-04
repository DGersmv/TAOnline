@echo off
chcp 65001 >nul
echo ========================================
echo Экспорт ключа с правильными параметрами
echo ========================================
echo.

REM Попробуем разные варианты алиаса
set KEYSTORE_PATH=C:\Рабочая\Android\taonline-release-key.jks
set OUTPUT_FILE=C:\Рабочая\Android\TAOnline\pepk_out.zip
set ENCRYPTION_KEY=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f

REM Проверка файла
if not exist "%KEYSTORE_PATH%" (
    echo ❌ Keystore не найден: %KEYSTORE_PATH%
    echo.
    echo Попробуем найти файл...
    echo.
    
    REM Проверяем альтернативные пути
    if exist "C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks" (
        echo ✅ Найден файл в: C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks
        echo.
        set /p copy_file="Скопировать файл в C:\Рабочая\Android\? (y/n): "
        if /i "%copy_file%"=="y" (
            copy "C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks" "%KEYSTORE_PATH%"
            echo ✅ Файл скопирован
        ) else (
            set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks
            echo Используем файл из исходного расположения
        )
    )
)

if not exist "%KEYSTORE_PATH%" (
    echo ❌ Файл keystore не найден!
    echo.
    echo Пожалуйста, укажите правильный путь к keystore файлу.
    pause
    exit /b 1
)

echo ✅ Keystore найден: %KEYSTORE_PATH%
echo.

REM Проверка pepk.jar
if not exist "pepk.jar" (
    echo ❌ Файл pepk.jar не найден в текущей директории!
    echo.
    echo Текущая директория: %CD%
    echo.
    echo Скачайте pepk.jar с:
    echo https://github.com/google/play-emm-server/tree/master/pepk
    echo.
    pause
    exit /b 1
)

echo ✅ pepk.jar найден
echo.

echo ========================================
echo Попытка экспорта с разными алиасами
echo ========================================
echo.

REM Попробуем первый алиас (тот что вы использовали)
echo Попытка 1: Алиас = taonline-release-key
echo.
java -jar pepk.jar ^
  --keystore "%KEYSTORE_PATH%" ^
  --alias taonline-release-key ^
  --output "%OUTPUT_FILE%" ^
  --encryptionkey=%ENCRYPTION_KEY% ^
  --include-cert

if %errorlevel% equ 0 (
    echo.
    echo ✅ УСПЕХ! Экспорт завершен с алиасом taonline-release-key
    goto :success
)

echo.
echo Попытка 2: Алиас = taonline-key (стандартный)
echo.
java -jar pepk.jar ^
  --keystore "%KEYSTORE_PATH%" ^
  --alias taonline-key ^
  --output "%OUTPUT_FILE%" ^
  --encryptionkey=%ENCRYPTION_KEY% ^
  --include-cert

if %errorlevel% equ 0 (
    echo.
    echo ✅ УСПЕХ! Экспорт завершен с алиасом taonline-key
    goto :success
)

echo.
echo Попытка 3: Алиас = key (если создавали без алиаса)
echo.
java -jar pepk.jar ^
  --keystore "%KEYSTORE_PATH%" ^
  --alias key ^
  --output "%OUTPUT_FILE%" ^
  --encryptionkey=%ENCRYPTION_KEY% ^
  --include-cert

if %errorlevel% equ 0 (
    echo.
    echo ✅ УСПЕХ! Экспорт завершен с алиасом key
    goto :success
)

echo.
echo ❌ Все попытки не удались
echo.
echo Возможные причины:
echo 1. Неправильный алиас - проверьте список алиасов командой:
echo    keytool -list -v -keystore "%KEYSTORE_PATH%"
echo.
echo 2. Неправильный пароль - убедитесь, что вводите правильный пароль
echo.
echo 3. Файл поврежден - попробуйте создать keystore заново
echo.
pause
exit /b 1

:success
echo.
echo ========================================
echo ✅ Экспорт успешно завершен!
echo ========================================
echo.
echo Файл создан: %OUTPUT_FILE%
echo.
echo Следующий шаг: Загрузите pepk_out.zip в Google Play Console
echo.
pause


