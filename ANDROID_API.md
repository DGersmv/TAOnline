# Документация API для Android-приложения

## Базовый URL API

- **Production:** `https://tashi-ani.ru`
- **IP:** `89.104.67.209`
- Все API endpoints начинаются с `/api/`

## Deep Links

### Для админа (загрузка фото)

**Схема:** `tashi-ani://upload`

**Параметры:**
- `userId` (required, number) - ID заказчика
- `objectId` (required, number) - ID объекта заказчика

**Пример:**
```
tashi-ani://upload?userId=3&objectId=1
```

**Описание:**
Открывает экран загрузки фото в Android-приложении для указанного объекта заказчика. Фото будут загружены в альбом "Все фото" объекта.

### Для заказчика (просмотр фото)

**Схема:** `tashi-ani://view`

**Параметры:**
- `email` (required, string) - Email заказчика
- `objectId` (required, number) - ID объекта

**Пример:**
```
tashi-ani://view?email=user%40example.com&objectId=1
```

**Описание:**
Открывает экран просмотра фото в Android-приложении. Показываются только фото, доступные заказчику (`isVisibleToCustomer = true`).

## Аутентификация

### Для админа

**Endpoint для входа:** `POST https://tashi-ani.ru/api/auth/login`

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "email": "admin@example.com",
  "password": "password"
}
```

**Успешный ответ (200):**
```json
{
  "success": true,
  "message": "Успешная аутентификация",
  "user": {
    "id": 1,
    "email": "admin@example.com",
    "name": "Admin",
    "role": "MASTER",
    "status": "ACTIVE"
  },
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Ошибки:**
- `400` - Email и пароль обязательны
- `401` - Неверный email или пароль
- `500` - Ошибка аутентификации

**Использование токена:**
После получения токена использовать его в заголовке `Authorization: Bearer {token}` для всех запросов админа.

### Для заказчика

**Endpoint для входа:** `POST https://tashi-ani.ru/api/auth/login`

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "email": "customer@example.com",
  "password": "password"
}
```

**Успешный ответ (200):**
```json
{
  "success": true,
  "message": "Успешная аутентификация",
  "user": {
    "id": 3,
    "email": "customer@example.com",
    "name": "Иван Петров",
    "role": "USER",
    "status": "ACTIVE"
  },
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Ошибки:**
- `400` - Email и пароль обязательны
- `401` - Неверный email или пароль
- `500` - Ошибка аутентификации

**Использование:**
После входа заказчик может:
1. Использовать полученный токен (опционально, для некоторых запросов)
2. Или использовать email в query параметре `email` для всех endpoints заказчика

## API Endpoints

### Админ: Получение списка заказчиков

**Endpoint:** `GET https://tashi-ani.ru/api/admin/users`

**Headers:**
```
Authorization: Bearer {adminToken}
```

**Успешный ответ (200):**
```json
{
  "success": true,
  "users": [
    {
      "id": 3,
      "email": "user@example.com",
      "name": "Иван Петров",
      "role": "USER",
      "status": "ACTIVE",
      "createdAt": "2025-01-20T12:00:00.000Z",
      "lastLogin": "2025-01-20T15:30:00.000Z",
      "objects": [
        {
          "id": 1,
          "title": "Участок на ул. Ленина",
          "description": "Описание объекта",
          "status": "ACTIVE",
          "_count": {
            "photos": 25,
            "documents": 5,
            "projects": 2,
            "messages": 10
          }
        }
      ]
    }
  ]
}
```

**Ошибки:**
- `401` - Не авторизован (неверный или отсутствующий токен)
- `403` - Доступ запрещен (недостаточно прав)
- `500` - Внутренняя ошибка сервера

**Примечание:** Возвращаются все пользователи, включая админов. В приложении можно фильтровать по `role === 'USER'` для получения только заказчиков.

### Админ: Получение информации о заказчике

**Endpoint:** `GET https://tashi-ani.ru/api/admin/users/{userId}`

**Headers:**
```
Authorization: Bearer {adminToken}
```

**Успешный ответ (200):**
```json
{
  "success": true,
  "user": {
    "id": 3,
    "email": "user@example.com",
    "name": "Иван Петров",
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2025-01-20T12:00:00.000Z",
    "lastLogin": "2025-01-20T15:30:00.000Z",
    "objects": [
      {
        "id": 1,
        "title": "Участок на ул. Ленина",
        "description": "Описание объекта",
        "status": "ACTIVE",
        "createdAt": "2025-01-20T12:00:00.000Z"
      }
    ],
    "unreadMessagesCount": 2,
    "unreadCommentsCount": 5,
    "totalMessagesCount": 10,
    "totalCommentsCount": 15,
    "objectStats": [
      {
        "objectId": 1,
        "title": "Участок на ул. Ленина",
        "unreadMessagesCount": 1,
        "unreadCommentsCount": 3,
        "totalMessagesCount": 5,
        "totalCommentsCount": 8
      }
    ]
  }
}
```

**Ошибки:**
- `401` - Не авторизован
- `403` - Доступ запрещен (требуются права MASTER)
- `404` - Пользователь не найден
- `500` - Внутренняя ошибка сервера

### Админ: Получение информации об объекте

**Endpoint:** `GET https://tashi-ani.ru/api/admin/objects/{objectId}?userId={userId}`

**Headers:**
```
Authorization: Bearer {adminToken}
```

**Query параметры:**
- `userId` (required) - ID заказчика, владельца объекта

**Успешный ответ (200):**
```json
{
  "success": true,
  "object": {
    "id": 1,
    "title": "Участок на ул. Ленина",
    "description": "Описание объекта",
    "address": "ул. Ленина, 10",
    "status": "ACTIVE",
    "photos": [...],
    "documents": [...],
    "messages": [...],
    "projects": [...],
    "user": {
      "id": 3,
      "email": "user@example.com",
      "name": "Иван Петров"
    },
    "unreadMessagesCount": 2,
    "unreadCommentsCount": 5
  }
}
```

**Ошибки:**
- `401` - Не авторизован
- `403` - Доступ запрещен
- `404` - Объект не найден
- `500` - Внутренняя ошибка сервера

### Админ: Загрузка фото

**Endpoint:** `POST https://tashi-ani.ru/api/admin/objects/{objectId}/photos`

**Headers:**
```
Authorization: Bearer {adminToken}
```

**Body:** `multipart/form-data`
- `file` (File, обязательно) - файл для загрузки
- `isVisibleToCustomer` (boolean, опционально, по умолчанию `false`) - видимость фото для заказчика

**Поддерживаемые форматы:**
- Изображения: JPEG, PNG, GIF, WebP
- Видео: MP4, AVI, MOV

**Максимальный размер файла:** 50MB

**Успешный ответ (200):**
```json
{
  "success": true,
  "photo": {
    "id": 123,
    "filename": "1234567890-abc123.jpg",
    "originalName": "photo.jpg",
    "fileSize": 1024000,
    "mimeType": "image/jpeg",
    "isVisibleToCustomer": false,
    "uploadedAt": "2025-01-20T12:00:00.000Z",
    "url": "/uploads/objects/1/1234567890-abc123.jpg"
  }
}
```

**Ошибки:**
- `401` - Не авторизован (неверный или отсутствующий токен)
- `403` - Доступ запрещен (недостаточно прав)
- `400` - Неверные параметры (файл не найден, неподдерживаемый тип, файл слишком большой)
- `500` - Внутренняя ошибка сервера

**Примечание:** Фото загружаются в альбом "Все фото" (без привязки к папке, `folderId = null`).

### Заказчик: Получение списка объектов

**Endpoint:** `GET https://tashi-ani.ru/api/user/objects?email={email}`

**Query параметры:**
- `email` (required) - Email заказчика

**Описание:**
Возвращает список всех объектов заказчика с их статистикой (непрочитанные сообщения, комментарии и т.д.). Используется для отображения списка объектов в приложении.

**Успешный ответ (200):**
```json
{
  "success": true,
  "objects": [
    {
      "id": 1,
      "title": "Участок на ул. Ленина",
      "description": "Описание объекта",
      "address": "ул. Ленина, 10",
      "status": "ACTIVE",
      "createdAt": "2025-01-20T12:00:00.000Z",
      "unreadMessagesCount": 2,
      "unreadCommentsCount": 5,
      "totalMessagesCount": 10,
      "totalCommentsCount": 15
    }
  ]
}
```

**Ошибки:**
- `400` - Email обязателен
- `404` - Пользователь не найден
- `500` - Внутренняя ошибка сервера

**Query параметры:**
- `email` (required) - Email заказчика

**Успешный ответ (200):**
```json
{
  "success": true,
  "objects": [
    {
      "id": 1,
      "title": "Участок на ул. Ленина",
      "description": "Описание объекта",
      "address": "ул. Ленина, 10",
      "status": "ACTIVE",
      "createdAt": "2025-01-20T12:00:00.000Z",
      "unreadMessagesCount": 2,
      "unreadCommentsCount": 5,
      "totalMessagesCount": 10,
      "totalCommentsCount": 15
    }
  ]
}
```

### Заказчик: Получение информации об объекте

**Endpoint:** `GET https://tashi-ani.ru/api/user/objects/{objectId}?email={email}`

**Query параметры:**
- `email` (required) - Email заказчика

**Описание:**
Возвращает полную информацию об объекте, включая все фото, доступные для заказчика (`isVisibleToCustomer = true`), документы, сообщения, проекты и статистику. Используется для отображения детальной информации об объекте.

**Успешный ответ (200):**
```json
{
  "success": true,
  "object": {
    "id": 1,
    "title": "Участок на ул. Ленина",
    "description": "Описание объекта",
    "address": "ул. Ленина, 10",
    "status": "ACTIVE",
    "createdAt": "2025-01-20T12:00:00.000Z",
    "photos": [
      {
        "id": 123,
        "filename": "photo.jpg",
        "originalName": "photo.jpg",
        "uploadedAt": "2025-01-20T12:00:00.000Z",
        "folder": {
          "id": 1,
          "name": "Дом"
        },
        "unreadCommentsCount": 2
      }
    ],
    "documents": [...],
    "messages": [...],
    "projects": [...],
    "unreadMessagesCount": 2,
    "unreadCommentsCount": 5,
    "totalMessagesCount": 10,
    "totalCommentsCount": 15
  }
}
```

**Ошибки:**
- `400` - Email обязателен
- `404` - Объект не найден или нет доступа
- `500` - Внутренняя ошибка сервера

**Примечание:** 
- В ответе уже включены все фото, доступные для заказчика
- Если нужен только список фото без другой информации, используйте `/api/user/objects/{objectId}/photos`

### Заказчик: Получение списка папок объекта

**Endpoint:** `GET https://tashi-ani.ru/api/user/objects/{objectId}/folders?email={email}`

**Query параметры:**
- `email` (required) - Email заказчика

**Описание:**
Возвращает список всех папок объекта, которые содержат фото, доступные для заказчика. Включает папку "Все фото" с общим количеством фото.

**Успешный ответ (200):**
```json
{
  "success": true,
  "folders": [
    {
      "id": null,
      "name": "Все фото",
      "orderIndex": -1,
      "photoCount": 25,
      "createdAt": "2025-01-20T12:00:00.000Z"
    },
    {
      "id": 1,
      "name": "Дом",
      "orderIndex": 0,
      "photoCount": 10,
      "createdAt": "2025-01-20T12:00:00.000Z"
    },
    {
      "id": 2,
      "name": "Участок",
      "orderIndex": 1,
      "photoCount": 15,
      "createdAt": "2025-01-20T12:00:00.000Z"
    }
  ]
}
```

**Ошибки:**
- `400` - Email не предоставлен или неверный ID объекта
- `404` - Объект не найден или нет доступа
- `500` - Внутренняя ошибка сервера

**Примечание:** 
- Папка "Все фото" всегда имеет `id: null` и `orderIndex: -1`
- Папка "Все фото" отображается только если есть хотя бы одно фото
- В список попадают только папки, которые содержат хотя бы одно фото с `isVisibleToCustomer = true`

### Заказчик: Получение списка фото объекта

**Endpoint:** `GET https://tashi-ani.ru/api/user/objects/{objectId}/photos?email={email}&folderId={folderId}`

**Query параметры:**
- `email` (required) - Email заказчика
- `folderId` (optional) - ID папки для фильтрации. Если не указан или `null`, возвращаются все фото. Если указан конкретный ID, возвращаются только фото этой папки.

**Описание:**
Возвращает список фото объекта, доступных для заказчика, с информацией о папках и комментариях. Поддерживает фильтрацию по папке.

**Примеры использования:**
- `GET .../photos?email=user@example.com` - все фото объекта
- `GET .../photos?email=user@example.com&folderId=1` - только фото из папки с ID=1
- `GET .../photos?email=user@example.com&folderId=null` - только фото без папки

**Успешный ответ (200):**
```json
{
  "success": true,
  "photos": [
    {
      "id": 123,
      "filename": "photo.jpg",
      "originalName": "photo.jpg",
      "uploadedAt": "2025-01-20T12:00:00.000Z",
      "folder": {
        "id": 1,
        "name": "Дом"
      },
      "comments": [
        {
          "id": 1,
          "content": "Комментарий к фото",
          "createdAt": "2025-01-20T12:00:00.000Z",
          "user": {
            "name": "Админ",
            "email": "admin@example.com"
          }
        }
      ]
    }
  ]
}
```

**Ошибки:**
- `400` - Email не предоставлен или неверный ID объекта
- `404` - Объект не найден или нет доступа
- `500` - Внутренняя ошибка сервера

**Примечание:** 
- Возвращаются только фото, видимые для заказчика (`isVisibleToCustomer = true`)
- Фото без папки имеют `folder: null`
- Комментарии отсортированы по дате создания (новые первыми)

### Заказчик: Получение файла фото

**Endpoint:** `GET https://tashi-ani.ru/api/uploads/objects/{objectId}/{filename}?email={email}`

**Query параметры:**
- `email` (required) - Email заказчика

**Ответ:** Бинарный файл (изображение или видео)

**Ошибки:**
- `400` - Email не предоставлен
- `404` - Фото не найдено или нет доступа
- `500` - Внутренняя ошибка сервера

## Примеры использования

### Получение списка заказчиков (админ)

```kotlin
// Пример на Kotlin
val url = "https://tashi-ani.ru/api/admin/users"
val request = Request.Builder()
    .url(url)
    .addHeader("Authorization", "Bearer $adminToken")
    .get()
    .build()

val response = client.newCall(request).execute()
val responseBody = response.body()?.string()
val jsonResponse = JSONObject(responseBody)

if (jsonResponse.getBoolean("success")) {
    val usersArray = jsonResponse.getJSONArray("users")
    val customers = mutableListOf<User>()
    
    for (i in 0 until usersArray.length()) {
        val user = usersArray.getJSONObject(i)
        if (user.getString("role") == "USER") {
            // Это заказчик
            customers.add(parseUser(user))
        }
    }
}
```

### Получение информации о заказчике (админ)

```kotlin
// Пример на Kotlin
val url = "https://tashi-ani.ru/api/admin/users/$userId"
val request = Request.Builder()
    .url(url)
    .addHeader("Authorization", "Bearer $adminToken")
    .get()
    .build()

val response = client.newCall(request).execute()
val responseBody = response.body()?.string()
val jsonResponse = JSONObject(responseBody)

if (jsonResponse.getBoolean("success")) {
    val user = jsonResponse.getJSONObject("user")
    val unreadMessages = user.getInt("unreadMessagesCount")
    val unreadComments = user.getInt("unreadCommentsCount")
    // ...
}
```

### Вход админа (получение токена)

```kotlin
// Пример на Kotlin
val url = "https://tashi-ani.ru/api/auth/login"
val json = JSONObject().apply {
    put("email", "2277277@bk.ru")
    put("password", "admin123")
}

val requestBody = RequestBody.create(
    MediaType.parse("application/json"), 
    json.toString()
)

val request = Request.Builder()
    .url(url)
    .post(requestBody)
    .addHeader("Content-Type", "application/json")
    .build()

val response = client.newCall(request).execute()
val responseBody = response.body()?.string()
val jsonResponse = JSONObject(responseBody)

if (jsonResponse.getBoolean("success")) {
    val token = jsonResponse.getString("token")
    val user = jsonResponse.getJSONObject("user")
    // Сохранить токен для дальнейших запросов
}
```

### Загрузка фото (админ)

```kotlin
// Пример на Kotlin
val url = "https://tashi-ani.ru/api/admin/objects/$objectId/photos"
val request = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("file", "photo.jpg", 
        RequestBody.create(MediaType.parse("image/jpeg"), file))
    .addFormDataPart("isVisibleToCustomer", "false")
    .build()

val httpRequest = Request.Builder()
    .url(url)
    .addHeader("Authorization", "Bearer $adminToken")
    .post(request)
    .build()

val response = client.newCall(httpRequest).execute()
```

### Вход заказчика (получение информации)

```kotlin
// Пример на Kotlin
val url = "https://tashi-ani.ru/api/auth/login"
val json = JSONObject().apply {
    put("email", "customer@example.com")
    put("password", "password")
}

val requestBody = RequestBody.create(
    MediaType.parse("application/json"), 
    json.toString()
)

val request = Request.Builder()
    .url(url)
    .post(requestBody)
    .addHeader("Content-Type", "application/json")
    .build()

val response = client.newCall(request).execute()
val responseBody = response.body()?.string()
val jsonResponse = JSONObject(responseBody)

if (jsonResponse.getBoolean("success")) {
    val user = jsonResponse.getJSONObject("user")
    val email = user.getString("email")
    // Сохранить email для дальнейших запросов
}
```

### Получение списка объектов (заказчик)

```kotlin
// Пример на Kotlin
val url = "https://tashi-ani.ru/api/user/objects?email=${URLEncoder.encode(email, "UTF-8")}"
val request = Request.Builder()
    .url(url)
    .get()
    .build()

val response = client.newCall(request).execute()
val responseBody = response.body()?.string()
val jsonResponse = JSONObject(responseBody)

if (jsonResponse.getBoolean("success")) {
    val objectsArray = jsonResponse.getJSONArray("objects")
    // Обработать список объектов
}
```

### Получение списка папок (заказчик)

```kotlin
// Пример на Kotlin
val url = "https://tashi-ani.ru/api/user/objects/$objectId/folders?email=${URLEncoder.encode(email, "UTF-8")}"
val request = Request.Builder()
    .url(url)
    .get()
    .build()

val response = client.newCall(request).execute()
val responseBody = response.body()?.string()
val jsonResponse = JSONObject(responseBody)

if (jsonResponse.getBoolean("success")) {
    val foldersArray = jsonResponse.getJSONArray("folders")
    // Обработать список папок
    // Первая папка обычно "Все фото" с id = null
}
```

### Получение списка фото (заказчик)

```kotlin
// Пример на Kotlin - получить все фото
val url = "https://tashi-ani.ru/api/user/objects/$objectId/photos?email=${URLEncoder.encode(email, "UTF-8")}"
val request = Request.Builder()
    .url(url)
    .get()
    .build()

val response = client.newCall(request).execute()
val responseBody = response.body()?.string()
val jsonResponse = JSONObject(responseBody)

if (jsonResponse.getBoolean("success")) {
    val photosArray = jsonResponse.getJSONArray("photos")
    // Обработать список фото
}

// Пример - получить фото только из конкретной папки
val folderId = 1
val urlWithFolder = "https://tashi-ani.ru/api/user/objects/$objectId/photos?email=${URLEncoder.encode(email, "UTF-8")}&folderId=$folderId"
val request2 = Request.Builder()
    .url(urlWithFolder)
    .get()
    .build()
// ...
```

### Получение файла фото (заказчик)

```kotlin
// Пример на Kotlin
val url = "https://tashi-ani.ru/api/uploads/objects/$objectId/$filename?email=${URLEncoder.encode(email, "UTF-8")}"
val request = Request.Builder()
    .url(url)
    .get()
    .build()

val response = client.newCall(request).execute()
if (response.isSuccessful) {
    val imageBytes = response.body()?.bytes()
    // Показать изображение
}
```

## Обработка ошибок

Все API endpoints возвращают JSON с полем `success`:

**Успех:**
```json
{
  "success": true,
  ...
}
```

**Ошибка:**
```json
{
  "success": false,
  "message": "Описание ошибки"
}
```

**HTTP статус коды:**
- `200` - Успешный запрос
- `400` - Неверные параметры запроса
- `401` - Не авторизован
- `403` - Доступ запрещен
- `404` - Ресурс не найден
- `500` - Внутренняя ошибка сервера

## Логика работы

### Админ: Загрузка фото

1. Админ на сайте выбирает заказчика и объект
2. Нажимает "Загрузить с телефона" в панели "Все фото"
3. Сайт открывает deep link: `tashi-ani://upload?userId={userId}&objectId={objectId}`
4. Android-приложение получает deep link и извлекает параметры
5. Приложение показывает экран загрузки фото с информацией о заказчике и объекте
6. При загрузке фото приложение отправляет POST запрос на `/api/admin/objects/{objectId}/photos`
7. Фото сохраняется в альбом "Все фото" (folderId = null)

### Заказчик: Просмотр фото (вариант 1 - через deep link с сайта)

1. Заказчик на сайте выбирает объект
2. Нажимает "Открыть в приложении" в панели фото
3. Сайт открывает deep link: `tashi-ani://view?email={email}&objectId={objectId}`
4. Android-приложение получает deep link и извлекает параметры
5. Приложение загружает список фото через `GET /api/user/objects/{objectId}/photos?email={email}`
6. Приложение отображает фото, доступные для заказчика (`isVisibleToCustomer = true`)
7. При просмотре фото приложение загружает файл через `GET /api/uploads/objects/{objectId}/{filename}?email={email}`

### Заказчик: Просмотр фото (вариант 2 - прямое открытие приложения)

1. Заказчик открывает приложение
2. Входит через `POST /api/auth/login` с email и паролем
3. Получает информацию о пользователе (email, id, name)
4. Загружает список своих объектов через `GET /api/user/objects?email={email}`
5. Пользователь выбирает объект из списка
6. Приложение загружает список папок через `GET /api/user/objects/{objectId}/folders?email={email}`
   - Получает папки, содержащие фото, доступные для заказчика
   - В списке всегда есть папка "Все фото" (id = null) с общим количеством фото
7. Приложение отображает список папок, включая "Все фото"
8. При выборе папки:
   - Если выбрана "Все фото" (id = null): загружает все фото через `GET /api/user/objects/{objectId}/photos?email={email}`
   - Если выбрана конкретная папка: загружает фото этой папки через `GET /api/user/objects/{objectId}/photos?email={email}&folderId={folderId}`
9. Приложение отображает:
   - Список фото выбранной папки (только с `isVisibleToCustomer = true`)
   - Превью фото
   - Информацию о папке, к которой привязано фото
10. При просмотре конкретного фото:
    - Загружает файл через `GET /api/uploads/objects/{objectId}/{filename}?email={email}`
    - Показывает комментарии к фото (если есть)
    - Отмечает комментарии как прочитанные

**Важно:** 
- Заказчик видит только фото, для которых `isVisibleToCustomer = true`
- Папка "Все фото" показывает все доступные фото объекта
- При выборе конкретной папки показываются только фото из этой папки
- Фото без папки (`folderId = null`) можно посмотреть, выбрав `folderId=null` в запросе

## Дополнительные замечания

1. **Валидация email:** Убедитесь, что email правильно кодируется в URL (используйте `URLEncoder.encode()`)

2. **Обработка ошибок сети:** Реализуйте retry логику для сетевых запросов

3. **Кэширование:** Рассмотрите возможность кэширования списка фото для офлайн-доступа

4. **Прогресс загрузки:** Для больших файлов показывайте прогресс загрузки

5. **Обработка deep links:** Убедитесь, что приложение правильно обрабатывает deep links, даже если оно было закрыто

