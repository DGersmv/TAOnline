#!/bin/bash

echo "========================================"
echo "Сборка релизной версии для RuStore"
echo "========================================"
echo ""

# Проверяем наличие keystore
if [ ! -f "taonline-release-key.jks" ]; then
    echo "❌ Файл taonline-release-key.jks не найден!"
    echo ""
    echo "Запустите create-keystore.sh для создания keystore"
    exit 1
fi

if [ ! -f "keystore.properties" ]; then
    echo "❌ Файл keystore.properties не найден!"
    echo ""
    echo "Запустите create-keystore.sh для создания keystore"
    exit 1
fi

echo "Очистка предыдущих сборок..."
./gradlew clean

echo ""
echo "Сборка AAB (Android App Bundle)..."
./gradlew bundleRelease

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Сборка успешно завершена!"
    echo ""
    echo "📦 Файл для загрузки в RuStore:"
    echo "   app/build/outputs/bundle/release/app-release.aab"
    echo ""
    echo "Следующие шаги:"
    echo "1. Зарегистрируйтесь на https://developer.rustore.ru/"
    echo "2. Создайте приложение в кабинете разработчика"
    echo "3. Загрузите файл app-release.aab"
    echo "4. Заполните информацию о приложении"
    echo "5. Отправьте на модерацию"
    echo ""
else
    echo ""
    echo "❌ Ошибка при сборке"
    exit 1
fi


