# 📄 Создание PEM сертификата для RuStore

## ✅ ZIP архив уже создан!

Теперь нужно создать PEM сертификат (шаг 4 в RuStore).

---

## 🚀 Быстрый способ:

### Запустите скрипт:

```batch
cd C:\Users\count\AndroidStudioProjects\TAOnline
create-pem-certificate.bat
```

Скрипт автоматически:
1. Найдет keytool
2. Покажет список алиасов
3. Создаст PEM файл

---

## 📝 Вручную:

### 1. Найдите keytool

Обычно находится в:
- `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`
- Или в папке JDK, которую использует Android Studio

### 2. Выполните команду:

```batch
cd C:\Рабочая\Android\TAOnline

"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -export -rfc -keystore "C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks" -alias taonline-key -file upload_certificate.pem
```

**Замените:**
- Путь к keytool на ваш реальный путь
- `taonline-key` на правильный алиас (если другой)

### 3. Введите пароль keystore

### 4. Файл будет создан

Файл `upload_certificate.pem` будет в папке `C:\Рабочая\Android\TAOnline\`

---

## 📤 Загрузка в RuStore:

1. Нажмите **"Выберите файл"** в RuStore (шаг 4)
2. Выберите файл `upload_certificate.pem`
3. Нажмите **"Отправить подпись"**

---

## ⚠️ Если алиас неправильный:

Проверьте правильный алиас:

```batch
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -keystore "C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks"
```

Введите пароль и посмотрите список алиасов.

---

## ✅ Готово!

После загрузки обоих файлов (ZIP и PEM) нажмите "Отправить подпись" в RuStore.


