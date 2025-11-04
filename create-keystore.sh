#!/bin/bash

echo "========================================"
echo "Создание Keystore для подписи приложения"
echo "========================================"
echo ""

# Проверяем, существует ли уже keystore
if [ -f "taonline-release-key.jks" ]; then
    echo "⚠️  Файл taonline-release-key.jks уже существует!"
    read -p "Перезаписать существующий keystore? (y/n): " overwrite
    if [ "$overwrite" != "y" ]; then
        echo "Операция отменена."
        exit 1
    fi
    rm -f taonline-release-key.jks
fi

echo "Введите данные для создания keystore:"
echo ""

# Запрашиваем пароли
read -s -p "Пароль хранилища (store password): " keystore_password
echo ""
read -s -p "Пароль ключа (key password): " key_password
echo ""
echo ""

echo "Введите данные для сертификата:"
echo ""

read -p "Ваше имя/название: " name
read -p "Организация: " org
read -p "Город: " city
read -p "Регион/Область: " state
read -p "Страна (2 буквы, например RU): " country

echo ""
echo "Создание keystore..."

keytool -genkey -v \
    -keystore taonline-release-key.jks \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -alias taonline-key \
    -storepass "$keystore_password" \
    -keypass "$key_password" \
    -dname "CN=$name, OU=$org, O=$org, L=$city, ST=$state, C=$country"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Keystore успешно создан: taonline-release-key.jks"
    echo ""
    
    # Создаем файл keystore.properties
    cat > keystore.properties << EOF
# Keystore пароли для подписи приложения
# НЕ коммитьте этот файл в Git!
KEYSTORE_PASSWORD=$keystore_password
KEY_PASSWORD=$key_password
EOF
    
    echo "✅ Файл keystore.properties создан"
    echo ""
    echo "⚠️  ВАЖНО: Сохраните эти данные в безопасном месте:"
    echo "   - Файл: taonline-release-key.jks"
    echo "   - Пароль хранилища: $keystore_password"
    echo "   - Пароль ключа: $key_password"
    echo "   - Алиас: taonline-key"
    echo ""
    echo "Без этих данных вы не сможете обновлять приложение!"
else
    echo ""
    echo "❌ Ошибка при создании keystore"
    exit 1
fi


