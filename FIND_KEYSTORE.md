# 🔍 Найден ваш keystore!

## ✅ Файл существует:

**Путь:** `C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks`

## ⚠️ Важно:

Вы используете неправильное расширение:
- ❌ Неправильно: `TAOnline.keystore`
- ✅ Правильно: `taonline-release-key.jks`

`.jks` и `.keystore` - это одно и то же, просто разные расширения!

---

## ✅ Правильная команда:

```batch
cd C:\Рабочая\Android\TAOnline

java -jar pepk.jar --keystore "C:\Users\count\AndroidStudioProjects\TAOnline\taonline-release-key.jks" --alias taonline-key --output pepk_out.zip --encryptionkey=00006b84f2e831c8118adb39ec405e1618ec9953ff3ad759dbf5c07a3095abf65531928a9fe49be549e9acfc55462fc44f5ebc28224cd951c41326edc88bc31e4022161f --include-cert
```

**Обратите внимание:**
- Используйте **`.jks`** вместо `.keystore`
- Используйте полный путь в кавычках
- Алиас может быть `taonline-key` или другой (нужно проверить)

---

## 🔍 Проверка алиаса:

Если команда не работает, проверьте правильный алиас:

```batch
cd C:\Users\count\AndroidStudioProjects\TAOnline

"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -keystore taonline-release-key.jks
```

Введите пароль и посмотрите список алиасов.

---

## 📝 Быстрое решение:

1. Убедитесь, что `pepk.jar` находится в `C:\Рабочая\Android\TAOnline\`
2. Используйте правильную команду выше (с `.jks` вместо `.keystore`)
3. Если алиас неправильный, проверьте через keytool


