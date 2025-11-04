# Как создать Keystore для подписи приложения

## ⚠️ ВАЖНО: Keystore нужен для публикации в магазинах приложений!

Без keystore вы не сможете обновлять приложение в RuStore. Сохраните keystore и пароли в безопасном месте!

---

## Способ 1: Через Android Studio (РЕКОМЕНДУЕТСЯ - самый простой)

### Шаг 1: Откройте Android Studio
1. Откройте проект TAOnline в Android Studio

### Шаг 2: Создайте Keystore
1. Перейдите: **Build** → **Generate Signed Bundle / APK**
2. В появившемся окне выберите **Android App Bundle** (или APK, если нужно)
3. Нажмите **Create new...** (кнопка для создания нового keystore)
4. Откроется окно "New Key Store"

### Шаг 3: Заполните данные Keystore

**Путь к файлу:**
```
C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks
```
(Или нажмите кнопку папки и выберите корневую папку проекта)

**Пароли:**
- **Key store password**: придумайте пароль (например: `2201`) - **ЗАПОМНИТЕ ЕГО!**
- **Key alias**: `taonline-key`
- **Key password**: можно использовать тот же пароль, что и Key store password (например: `2201`)

**Срок действия:**
- **Validity (years)**: `25` или `10000` (долгий срок)

**Данные сертификата:**
- **First and Last Name**: Ваше имя или название организации
- **Organizational Unit**: Подразделение (например: "Development")
- **Organization**: Название организации (например: "TAOnline")
- **City or Locality**: Город (например: "Москва")
- **State or Province**: Область/Регион (например: "Московская область")
- **Country Code (XX)**: `RU` (две буквы)

### Шаг 4: Сохраните данные
- Android Studio покажет все данные - **СКОПИРУЙТЕ ИХ ИЛИ СДЕЛАЙТЕ СКРИНШОТ!**
- Нажмите **OK**

### Шаг 5: Завершите создание AAB/APK
1. Выберите **release** вариант
2. Нажмите **Next**
3. Выберите папку для сохранения (или оставьте по умолчанию)
4. Нажмите **Create**

### Шаг 6: Создайте файл keystore.properties
После создания keystore, создайте файл `keystore.properties` в корне проекта:

```properties
KEYSTORE_PASSWORD=ваш_пароль_хранилища
KEY_PASSWORD=ваш_пароль_ключа
```

Например:
```properties
KEYSTORE_PASSWORD=2201
KEY_PASSWORD=2201
```

---

## Способ 2: Через командную строку (если keytool доступен)

### Шаг 1: Найдите keytool.exe

Обычно keytool находится в:
- `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`
- Или в папке JDK

**Как найти в Android Studio:**
1. File → Project Structure → SDK Location
2. Посмотрите путь к **JDK Location**
3. keytool будет в: `[JDK Location]\bin\keytool.exe`

### Шаг 2: Запустите команду

Откройте командную строку в папке проекта и выполните:

```batch
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkey -v -keystore taonline-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias taonline-key -storepass 2201 -keypass 2201 -dname "CN=Ваше Имя, OU=Подразделение, O=Организация, L=Город, ST=Область, C=RU"
```

**Замените:**
- Путь к keytool.exe на ваш реальный путь
- `2201` на ваши пароли
- Данные в `-dname` на ваши данные

### Шаг 3: Создайте keystore.properties

Создайте файл `keystore.properties` в корне проекта:

```properties
KEYSTORE_PASSWORD=2201
KEY_PASSWORD=2201
```

---

## Способ 3: Использовать скрипт create-keystore-simple.bat

Запустите `create-keystore-simple.bat` - он попросит указать путь к keytool, если не найдет его автоматически.

---

## ✅ После создания keystore

1. **Файл `taonline-release-key.jks`** должен быть в корне проекта
2. **Файл `keystore.properties`** должен быть в корне проекта
3. **Сохраните в безопасном месте:**
   - Файл keystore
   - Пароли
   - Алиас (taonline-key)

## ⚠️ ВАЖНО ДЛЯ БУДУЩЕГО

**БЕЗ ЭТИХ ДАННЫХ ВЫ НЕ СМОЖЕТЕ ОБНОВЛЯТЬ ПРИЛОЖЕНИЕ!**

Сохраните:
- Файл `taonline-release-key.jks`
- Пароль хранилища
- Пароль ключа
- Алиас: `taonline-key`

---

## Проверка

После создания keystore, файлы должны быть в корне проекта:
- ✅ `taonline-release-key.jks`
- ✅ `keystore.properties`

Если файлы созданы, можно собирать подписанный AAB:
```batch
build-release.bat
```

Или через Android Studio: Build → Generate Signed Bundle / APK


