@echo off
chcp 65001 >nul
echo ========================================
echo Диагностика keystore
echo ========================================
echo.

set KEYSTORE_PATH=C:\Рабочая\Android\taonline-release-key.jks

echo Проверка файла...
if exist "%KEYSTORE_PATH%" (
    echo ✅ Файл найден: %KEYSTORE_PATH%
    echo.
    echo Размер файла:
    dir "%KEYSTORE_PATH%"
) else (
    echo ❌ Файл НЕ найден: %KEYSTORE_PATH%
    echo.
    echo Поиск файлов .jks в C:\Рабочая\Android\...
    dir /s "C:\Рабочая\Android\*.jks" 2>nul
    echo.
    echo Поиск файлов .jks в C:\Users\count\AndroidStudioProjects\TAOnline\...
    dir /s "C:\Users\count\AndroidStudioProjects\TAOnline\*.jks" 2>nul
)

echo.
echo ========================================
echo Проверка алиасов в keystore
echo ========================================
echo.
echo Введите пароль keystore, чтобы увидеть список алиасов:
echo.

if exist "%KEYSTORE_PATH%" (
    keytool -list -v -keystore "%KEYSTORE_PATH%"
) else (
    echo Не могу проверить алиасы - файл не найден
)

pause


