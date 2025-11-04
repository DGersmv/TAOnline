# Экспорт ключа для RuStore

## ⚠️ ВАЖНО: Для RuStore обычно НЕ нужен pepk.jar!

RuStore использует обычный keystore напрямую. Инструмент `pepk.jar` нужен только для **Google Play Console** (App Signing).

---

## Для RuStore:

Просто используйте ваш keystore файл напрямую:
- Файл: `taonline-release-key.jks`
- Алиас: `taonline-key`

---

## Если всё же нужно экспортировать ключ (для Google Play):

### Проблемы в вашей команде:

1. **Неправильный путь к файлу:**
   - Вы ищете: `C:\Рабочая\Android\TAOnline.keystore`
   - Правильный путь зависит от того, где находится файл

2. **Неправильное имя файла:**
   - Вы ищете: `TAOnline.keystore`
   - Правильное имя: `taonline-release-key.jks` (или `.keystore`)

3. **Неправильный алиас:**
   - Вы используете: `ta-release-key`
   - Правильный алиас: `taonline-key`

---

## Правильная команда:

### Сначала найдите файл keystore:

```batch
cd C:\Рабочая\Android\TAOnline
dir *.jks *.keystore
```

Или если файл в другом месте:

```batch
cd C:\Users\count\AndroidStudioProjects\TAOnline
dir *.jks *.keystore
```

### Затем используйте правильную команду:

```batch
java -jar pepk.jar ^
  --keystore "ПОЛНЫЙ_ПУТЬ_К_ФАЙЛУ\taonline-release-key.jks" ^
  --alias taonline-key ^
  --output "C:\Рабочая\Android\TAOnline\pepk_out.zip" ^
  --encryptionkey=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f ^
  --include-cert
```

**Пример:**
```batch
java -jar pepk.jar ^
  --keystore "C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks" ^
  --alias taonline-key ^
  --output "C:\Рабочая\Android\TAOnline\pepk_out.zip" ^
  --encryptionkey=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f ^
  --include-cert
```

---

## Шаги для исправления:

1. **Найдите ваш keystore файл:**
   ```batch
   cd C:\Рабочая\Android\TAOnline
   dir /s *.jks
   ```

2. **Скопируйте keystore в нужную папку (если нужно):**
   ```batch
   copy "C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks" "C:\Рабочая\Android\TAOnline\taonline-release-key.jks"
   ```

3. **Используйте правильную команду:**
   ```batch
   cd C:\Рабочая\Android\TAOnline
   java -jar pepk.jar --keystore taonline-release-key.jks --alias taonline-key --output pepk_out.zip --encryptionkey=ВАШ_КЛЮЧ --include-cert
   ```

---

## Для RuStore:

**Вам НЕ нужен pepk.jar!** Просто используйте:
- Файл: `taonline-release-key.jks`
- Алиас: `taonline-key`
- Пароли (store password и key password)

Загрузите AAB файл напрямую в RuStore.


