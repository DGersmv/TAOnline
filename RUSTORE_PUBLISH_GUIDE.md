# Руководство по публикации приложения на RuStore

## Шаг 1: Создание Keystore для подписи приложения

### Важно: Сохраните keystore файл и пароли в безопасном месте!

1. Откройте терминал в корне проекта
2. Выполните команду для создания keystore:

```bash
keytool -genkey -v -keystore taonline-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias taonline-key -storepass ваш_пароль_хранилища -keypass ваш_пароль_ключа
```

**Параметры:**
- `taonline-release-key.jks` - имя файла keystore
- `validity 10000` - срок действия (примерно 27 лет)
- Заполните все данные (ФИО, организация, город, страна)

**Сохраните:**
- Файл `taonline-release-key.jks` (НЕ загружайте в Git!)
- Пароль хранилища (storepass)
- Пароль ключа (keypass)
- Алиас (taonline-key)

## Шаг 2: Настройка подписи в build.gradle.kts

Добавьте конфигурацию подписи в `app/build.gradle.kts`:

```kotlin
android {
    // ... существующий код ...
    
    signingConfigs {
        create("release") {
            storeFile = file("../taonline-release-key.jks")
            storePassword = project.findProperty("KEYSTORE_PASSWORD") as String? ?: ""
            keyAlias = "taonline-key"
            keyPassword = project.findProperty("KEY_PASSWORD") as String? ?: ""
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

## Шаг 3: Создание файла с паролями

Создайте файл `keystore.properties` в корне проекта (он будет в .gitignore):

```properties
KEYSTORE_PASSWORD=ваш_пароль_хранилища
KEY_PASSWORD=ваш_пароль_ключа
```

Добавьте в `app/build.gradle.kts` в начало файла:

```kotlin
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = java.util.Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
}
```

И обновите signingConfigs:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../taonline-release-key.jks")
        storePassword = keystoreProperties["KEYSTORE_PASSWORD"] as String
        keyAlias = "taonline-key"
        keyPassword = keystoreProperties["KEY_PASSWORD"] as String
    }
}
```

## Шаг 4: Обновление версии приложения

Перед каждой публикацией обновляйте версию в `app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 5  // Увеличьте на 1
    versionName = "1.3"  // Обновите версию
}
```

## Шаг 5: Сборка AAB (Android App Bundle)

RuStore предпочитает AAB формат. Выполните в терминале:

```bash
./gradlew bundleRelease
```

Или в Android Studio:
1. Build → Generate Signed Bundle / APK
2. Выберите "Android App Bundle"
3. Выберите release keystore
4. Соберите AAB

Файл будет в: `app/build/outputs/bundle/release/app-release.aab`

## Шаг 6: Регистрация на RuStore

1. Перейдите на https://developer.rustore.ru/
2. Зарегистрируйтесь как разработчик
3. Заполните профиль разработчика
4. Подтвердите аккаунт (может потребоваться верификация)

## Шаг 7: Создание приложения в RuStore

1. Войдите в кабинет разработчика
2. Нажмите "Создать приложение"
3. Заполните информацию:
   - Название: TAOnline
   - Категория: Бизнес / Утилиты
   - Краткое описание (до 80 символов)
   - Полное описание
   - Иконка приложения (512x512 px)
   - Скриншоты (минимум 2, максимум 8)
     - Разрешение: минимум 320px, максимум 3840px
     - Формат: PNG или JPEG
     - Соотношение сторон: 16:9 или 9:16

## Шаг 8: Загрузка приложения

1. В кабинете разработчика выберите ваше приложение
2. Перейдите в раздел "Версии"
3. Нажмите "Добавить версию"
4. Загрузите AAB файл (`app-release.aab`)
5. Заполните:
   - Что нового в этой версии
   - Поддерживаемые устройства (Android 7.0+)
   - Разрешения (если требуется)
6. Нажмите "Отправить на модерацию"

## Шаг 9: Модерация

- Обычно занимает 1-3 рабочих дня
- Проверяется безопасность, соответствие правилам, работа приложения
- После одобрения приложение будет доступно в RuStore

## Требования RuStore

1. **Минимальные требования:**
   - Android 7.0 (API 24) - ✅ у вас minSdk = 24
   - Target SDK 30+ - ⚠️ у вас targetSdk = 35 (отлично!)

2. **Обязательные материалы:**
   - Иконка приложения (512x512 px)
   - Скриншоты (минимум 2)
   - Описание на русском языке
   - Политика конфиденциальности (если собираете данные)

3. **Политика конфиденциальности:**
   - Если приложение собирает данные пользователей, нужна ссылка на политику конфиденциальности
   - Можно создать на GitHub Pages или другом хостинге

## Полезные ссылки

- RuStore для разработчиков: https://developer.rustore.ru/
- Документация: https://developer.rustore.ru/developer/
- Правила публикации: https://developer.rustore.ru/developer/rules/

## Важные замечания

⚠️ **Безопасность:**
- НИКОГДА не загружайте keystore файл в Git
- Добавьте `keystore.properties` и `*.jks` в `.gitignore`
- Храните пароли в безопасном месте
- Сделайте резервную копию keystore

⚠️ **Обновления:**
- При каждом обновлении увеличивайте `versionCode`
- Обновляйте `versionName` для пользователей
- RuStore поддерживает автоматические обновления

⚠️ **Тестирование:**
- Перед публикацией протестируйте релизную версию на реальных устройствах
- Проверьте все функции
- Убедитесь, что нет критических ошибок


