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
  -F "image=@stimulus.png;type=image/png"
```

Ответ `201`:
```json
{
  "id": 1,
  "name": "My Test",
  "imageUrl": "/tests/1/image",
  "aoi": [],
  "createdAt": "2025-01-15T12:00:00Z"
}
```

### Переименовать тест (ADMIN+)

```bash
curl -X PATCH $API_BASE_URL/tests/1/name \
  -H "Authorization: Bearer <admin_token>" \
  -d "name=Updated+Test"
```

Ответ `200`: TestResponse. Ответ `404` если тест не найден.

### Обновить AOI теста (ADMIN+)

```bash
curl -X PATCH $API_BASE_URL/tests/1/aoi \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_token>" \
  -d '{
    "aoi": [
      {"name":"zone1","color":"#FF0000","first_fixation":false,"points":[{"x":10,"y":20},{"x":110,"y":20},{"x":110,"y":120},{"x":10,"y":120}]},
      {"name":"zone2","color":"#00FF00","first_fixation":true,"points":[{"x":200,"y":50},{"x":300,"y":50},{"x":300,"y":150},{"x":200,"y":150}]}
    ]
  }'
```

Ответ `200`:
```json
{
  "testId": 1,
  "aoi": [
    {"name":"zone1","color":"#FF0000","first_fixation":false,"points":[...]},
    {"name":"zone2","color":"#00FF00","first_fixation":true,"points":[...]}
  ]
}
```

Ответ `404` если тест не найден.

### Получить или создать токен прохождения (ADMIN+)

```bash
curl -X POST $API_BASE_URL/tests/1/token \
  -H "Authorization: Bearer <admin_token>"
```

Ответ `200`:
```json
{"code":"12345678","testId":1}
```

### Получить тест по токену (без авторизации)

```bash
curl $API_BASE_URL/tests/by-token/12345678
```

Ответ `200`: TestResponse. Ответ `404` если токен не найден.

### Список тестов

```bash
curl "$API_BASE_URL/tests?name=stimulus" \
  -H "Authorization: Bearer <token>"
```

Ответ `200`:
```json
{
  "tests": [
    {
      "id": 1,
      "name": "My Test",
      "imageUrl": "/tests/1/image",
      "aoi": [],
      "createdAt": "2025-01-15T12:00:00Z"
    }
  ]
}
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

### Скачать изображение теста

```bash
curl $API_BASE_URL/tests/1/image \
  -H "Authorization: Bearer <token>" -o image.png
```

Ответ `200` (бинарные данные). Ответ `404` если изображение не найдено.

### AOI-статистика по тесту (ADMIN+)

```bash
curl $API_BASE_URL/tests/1/aoi-stats \
  -H "Authorization: Bearer <admin_token>"
```

Ответ `200`:
```json
{
  "aois": [
    {"name":"zone1","color":"#FF0000","hits":5,"total":10,"firstFixationRequired":false},
    {"name":"zone2","color":"#00FF00","hits":3,"total":10,"firstFixationRequired":true}
  ],
  "totalRecords": 10,
  "uniqueUsers": 7
}
```

## Прохождения (Records)

Поле `userLogin` в ответах берётся из JWT токена (для аутентифицированных запросов) или из поля `login` тела запроса (для анонимных по токену).

### Создать прохождение (аутентифицированный)

```bash
curl -X POST $API_BASE_URL/records \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "testId": 1,
    "startedAt": "2025-01-15T10:00:00Z",
    "finishedAt": "2025-01-15T10:05:00Z",
    "durationMs": 300000,
    "metrics": {
      "fixations": [],
      "roiMetrics": []
    }
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
  "metrics": {
    "fixations": [],
    "roiMetrics": []
  }
}
```

Ответ `400` (невалидные данные):
```json
{"error": "Duration must be non-negative"}
```

Ответ `404` (тест не найден):
```json
{"error": "Test not found"}
```

### Создать прохождение по токену (без авторизации)

```bash
curl -X POST $API_BASE_URL/records/unauthorized \
  -H "Content-Type: application/json" \
  -d '{
    "token": "12345678",
    "login": "participant@example.com",
    "startedAt": "2025-01-15T10:00:00Z",
    "finishedAt": "2025-01-15T10:05:00Z",
    "durationMs": 300000,
    "metrics": {
      "fixations": [],
      "roiMetrics": []
    }
  }'
```

Ответ `201`: аналогичен созданию аутентифицированным пользователем.

Ответ `404` (токен не найден):
```json
{"error": "Invalid token"}
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
      "createdAt": "2025-01-15T10:05:01Z",
      "metrics": {"fixations":[],"roiMetrics":[]}
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

### Фильтрация по AOI

```bash
# Только прохождения, где zone1 была зафиксирована (hit=true)
curl "$API_BASE_URL/records?testId=1&aoi.zone1=true" \
  -H "Authorization: Bearer <token>"

# Прохождения, где zone1 зафиксирована, а zone2 — нет
curl "$API_BASE_URL/records?testId=1&aoi.zone1=true&aoi.zone2=false" \
  -H "Authorization: Bearer <token>"
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

Ответ `200`: RecordResponse (см. выше). Ответ `404`:
```json
{"error": "Record not found"}
```

### Проверить синхронизацию AOI-метрик

```bash
curl "$API_BASE_URL/records/aoi-sync?testId=1" \
  -H "Authorization: Bearer <token>"
```

Ответ `200`:
```json
{
  "synced": false,
  "totalRecords": 10,
  "outOfSyncCount": 3
}
```

Ответ `404` (тест не найден):
```json
{"error": "Test not found"}
```

### Пересчитать AOI-метрики

```bash
curl -X POST "$API_BASE_URL/records/sync-aoi?testId=1" \
  -H "Authorization: Bearer <token>"
```

Ответ `204` (без тела). Ответ `404` (тест не найден):
```json
{"error": "Test not found"}
```
