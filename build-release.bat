@echo off
chcp 65001 >nul
echo ========================================
echo Сборка релизной версии для RuStore
echo ========================================
echo.

REM Проверяем наличие keystore
if not exist "taonline-release-key.jks" (
    echo ❌ Файл taonline-release-key.jks не найден!
    echo.
    echo Запустите create-keystore.bat для создания keystore
    pause
    exit /b 1
)

if not exist "keystore.properties" (
    echo ❌ Файл keystore.properties не найден!
    echo.
    echo Запустите create-keystore.bat для создания keystore
    pause
    exit /b 1
)

echo Очистка предыдущих сборок...
call gradlew clean

echo.
echo Сборка AAB (Android App Bundle)...
call gradlew bundleRelease

if %errorlevel% equ 0 (
    echo.
    echo ✅ Сборка успешно завершена!
    echo.
    echo 📦 Файл для загрузки в RuStore:
    echo    app\build\outputs\bundle\release\app-release.aab
    echo.
    echo Следующие шаги:
    echo 1. Зарегистрируйтесь на https://developer.rustore.ru/
    echo 2. Создайте приложение в кабинете разработчика
    echo 3. Загрузите файл app-release.aab
    echo 4. Заполните информацию о приложении
    echo 5. Отправьте на модерацию
    echo.
) else (
    echo.
    echo ❌ Ошибка при сборке
)

pause


