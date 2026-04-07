# Примеры запросов к API

Базовый URL: `$API_BASE_URL`

## Регистрация

```bash
curl -X POST $API_BASE_URL/auth/register \
  -H "Content-Type: application/json" \
  -d '{"login":"user@test.com","password":"secret123"}'
```

Ответ `201`:
```json
{"token":"eyJ..."}
```

Ответ `409` (пользователь существует):
```json
{"error":"User already exists"}
```

## Авторизация

```bash
curl -X POST $API_BASE_URL/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"user@test.com","password":"secret123"}'
```

Ответ `200`:
```json
{"token":"eyJ..."}
```

Ответ `401` (неверные данные):
```json
{"error":"Invalid credentials"}
```

## Использование токена

Для защищённых эндпоинтов передавайте токен в заголовке `Authorization`:

```bash
curl $API_BASE_URL/protected-endpoint \
  -H "Authorization: Bearer <token>"
```

Ответ `401` (невалидный/истёкший токен):
```
Token is not valid or has expired
```

## Получить роль текущего пользователя

```bash
curl $API_BASE_URL/auth/me/role \
  -H "Authorization: Bearer <token>"
```

Ответ `200`:
```json
{"role":"USER"}
```

## Создание пользователя (ADMIN+)

```bash
curl -X POST $API_BASE_URL/auth/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_token>" \
  -d '{"login":"newuser@test.com","password":"secret123","role":"USER"}'
```

Ответ `201`:
```json
{"id":2,"login":"newuser@test.com","role":"USER"}
```

Ответ `403` (недостаточно прав):
```json
{"error":"Forbidden"}
```

Ответ `409` (пользователь существует):
```json
{"error":"User already exists"}
```

## Тесты

### Создать тест (multipart, ADMIN+)

```bash
curl -X POST $API_BASE_URL/tests \
  -H "Authorization: Bearer <admin_token>" \
  -F "name=My Test" \
  -F "cover=@cover.png;type=image/png" \
  -F "images=@image1.jpg;type=image/jpeg" \
  -F "images=@image2.jpg;type=image/jpeg"
```

Ответ `201`:
```json
{
  "id": 1,
  "name": "My Test",
  "coverUrl": "/tests/1/cover",
  "imageUrls": ["/tests/1/images/0", "/tests/1/images/1"],
  "imageIds": [1, 2],
  "rois": [null, null],
  "createdAt": "2025-01-15T12:00:00Z"
}
```

### Обновить тест (multipart, ADMIN+)

```bash
curl -X PUT $API_BASE_URL/tests/1 \
  -H "Authorization: Bearer <admin_token>" \
  -F "name=Updated Test" \
  -F "cover=@new_cover.png;type=image/png" \
  -F "images=@new_image.jpg;type=image/jpeg"
```

Ответ `200`: аналогичен созданию. Ответ `404` если тест не найден.

### Обновить ROI изображения (ADMIN+)

```bash
curl -X PATCH $API_BASE_URL/tests/images/1/roi \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_token>" \
  -d '{"roi":"{\"x\":10,\"y\":20,\"w\":100,\"h\":50}"}'
```

Ответ `200`:
```json
{"imageId": 1, "roi": "{\"x\":10,\"y\":20,\"w\":100,\"h\":50}"}
```

Ответ `404` (изображение не найдено):
```json
{"error": "Image not found"}
```

### Список тестов

```bash
curl $API_BASE_URL/tests \
  -H "Authorization: Bearer <token>"
```

Ответ `200`:
```json
{"tests":[{"id":1,"name":"My Test","coverUrl":"/tests/1/cover","imageUrls":["/tests/1/images/0"],"imageIds":[1],"rois":[null],"createdAt":"..."}]}
```

### Получить тест по ID

```bash
curl $API_BASE_URL/tests/1 \
  -H "Authorization: Bearer <token>"
```

Ответ `200`: TestResponse. Ответ `404` если не найден.

### Удалить тест (ADMIN+)

```bash
curl -X DELETE $API_BASE_URL/tests/1 \
  -H "Authorization: Bearer <admin_token>"
```

Ответ `204` (без тела). Ответ `404` если не найден.

### Скачать обложку / изображение

```bash
# Обложка
curl $API_BASE_URL/tests/1/cover \
  -H "Authorization: Bearer <token>" -o cover.png

# Изображение по индексу (0-based)
curl $API_BASE_URL/tests/1/images/0 \
  -H "Authorization: Bearer <token>" -o image.jpg
```

## Прохождения (Records)

Поле `userLogin` в ответах заполняется автоматически из JWT токена (email аутентифицированного пользователя).

### Создать прохождение

```bash
curl -X POST $API_BASE_URL/records \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "testId": 1,
    "startedAt": "2025-01-15T10:00:00Z",
    "finishedAt": "2025-01-15T10:05:00Z",
    "durationMs": 300000,
    "items": [
      {"imageId": 1, "metrics": {"placeholderMetric": 0.85}},
      {"imageId": 2, "metrics": {"placeholderMetric": 0.92}}
    ]
  }'
```

Ответ `201`:
```json
{
  "id": 1,
  "testId": 1,
  "userLogin": "student@example.com",
  "startedAt": "2025-01-15T10:00:00Z",
  "finishedAt": "2025-01-15T10:05:00Z",
  "durationMs": 300000,
  "createdAt": "2025-01-15T10:05:01Z",
  "items": [
    {"id": 1, "imageId": 1, "metrics": {"placeholderMetric": 0.85}},
    {"id": 2, "imageId": 2, "metrics": {"placeholderMetric": 0.92}}
  ]
}
```

Ответ `400` (невалидные данные):
```json
{"error": "At least one item is required"}
```

Ответ `404` (тест не найден):
```json
{"error": "Test not found"}
```

### Список прохождений (с пагинацией и фильтрами)

```bash
curl "$API_BASE_URL/records?page=1&pageSize=20&testId=1&userLogin=student@example.com&from=2025-01-01T00:00:00Z&to=2025-12-31T23:59:59Z" \
  -H "Authorization: Bearer <token>"
```

Ответ `200`:
```json
{
  "items": [
    {
      "id": 1,
      "testId": 1,
      "userLogin": "student@example.com",
      "startedAt": "2025-01-15T10:00:00Z",
      "finishedAt": "2025-01-15T10:05:00Z",
      "durationMs": 300000,
      "createdAt": "2025-01-15T10:05:01Z"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

### Список уникальных участников (suggest)

```bash
curl "$API_BASE_URL/records/users/suggest?page=1&pageSize=20&testId=1" \
  -H "Authorization: Bearer <token>"
```

Ответ `200`:
```json
{
  "items": ["student@example.com", "another@example.com"],
  "page": 1,
  "pageSize": 20,
  "total": 2
}
```

### Получить прохождение по ID

```bash
curl $API_BASE_URL/records/1 \
  -H "Authorization: Bearer <token>"
```

Ответ `200`:
```json
{
  "id": 1,
  "testId": 1,
  "userLogin": "student@example.com",
  "startedAt": "2025-01-15T10:00:00Z",
  "finishedAt": "2025-01-15T10:05:00Z",
  "durationMs": 300000,
  "createdAt": "2025-01-15T10:05:01Z",
  "items": [
    {"id": 1, "imageId": 1, "metrics": {"placeholderMetric": 0.85}},
    {"id": 2, "imageId": 2, "metrics": {"placeholderMetric": 0.92}}
  ]
}
```

Ответ `404`:
```json
{"error": "Record not found"}
```
