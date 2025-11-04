# Инструкция по поиску keytool

## Проблема: "keytool" не является внутренней или внешней командой

Это означает, что `keytool` не найден в PATH. Вот несколько способов решения:

## Вариант 1: Использовать Android Studio (РЕКОМЕНДУЕТСЯ)

1. Откройте проект в Android Studio
2. Перейдите: **Build** → **Generate Signed Bundle / APK**
3. Выберите **Android App Bundle**
4. Нажмите **Create new...** для создания нового keystore
5. Заполните все поля
6. Android Studio автоматически создаст keystore и настроит его в проекте

## Вариант 2: Найти keytool вручную

`keytool` обычно находится в папке JDK. Проверьте следующие пути:

### Windows:
- `%LOCALAPPDATA%\Android\Sdk\jbr\bin\keytool.exe`
- `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`
- `C:\Program Files (x86)\Android\Android Studio\jbr\bin\keytool.exe`
- `%USERPROFILE%\AppData\Local\Android\Sdk\jbr\bin\keytool.exe`

### Как найти путь к JDK в Android Studio:
1. Откройте Android Studio
2. Перейдите: **File** → **Project Structure** → **SDK Location**
3. Посмотрите путь к **JDK Location**
4. keytool будет в: `[JDK Location]\bin\keytool.exe`

## Вариант 3: Использовать полный путь

После того как найдете keytool.exe, используйте его с полным путем:

```batch
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkey -v -keystore taonline-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias taonline-key -storepass ВАШ_ПАРОЛЬ1 -keypass ВАШ_ПАРОЛЬ2 -dname "CN=Ваше Имя, OU=Организация, O=Организация, L=Город, ST=Область, C=RU"
```

## Вариант 4: Установить JDK и добавить в PATH

1. Скачайте JDK с [Oracle](https://www.oracle.com/java/technologies/downloads/) или используйте OpenJDK
2. Установите JDK
3. Добавьте `[JDK PATH]\bin` в переменную окружения PATH

## Вариант 5: Использовать скрипт create-keystore-simple.bat

Запустите `create-keystore-simple.bat` - он попросит указать путь к keytool, если не найдет его автоматически.

---

**После создания keystore:**
- Файл `taonline-release-key.jks` будет создан в корне проекта
- Файл `keystore.properties` будет создан автоматически
- Сохраните эти файлы и пароли в безопасном месте!


