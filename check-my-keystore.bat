@echo off
chcp 65001 >nul
echo ========================================
echo Проверка keystore
echo ========================================
echo.

set KEYSTORE_PATH=C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks

echo Keystore найден: %KEYSTORE_PATH%
echo.
echo Проверка файла...
if exist "%KEYSTORE_PATH%" (
    echo ✅ Файл существует
    echo.
    echo Размер файла:
    dir "%KEYSTORE_PATH%"
    echo.
    echo ========================================
    echo Список алиасов в keystore:
    echo (Введите пароль keystore когда попросит)
    echo ========================================
    echo.
    keytool -list -v -keystore "%KEYSTORE_PATH%"
    echo.
    echo ========================================
    echo Скопируйте алиас из списка выше
    echo ========================================
) else (
    echo ❌ Файл не найден!
)

pause


