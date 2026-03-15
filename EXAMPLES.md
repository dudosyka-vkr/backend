# Примеры запросов к API

Базовый URL: `http://localhost:8080`

## Регистрация

```bash
curl -X POST http://localhost:8080/auth/register \
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
curl -X POST http://localhost:8080/auth/login \
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
curl http://localhost:8080/protected-endpoint \
  -H "Authorization: Bearer <token>"
```

Ответ `401` (невалидный/истёкший токен):
```
Token is not valid or has expired
```

## Прохождения (Records)

### Создать прохождение

```bash
curl -X POST http://localhost:8080/records \
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
curl "http://localhost:8080/records?page=1&pageSize=20&userLogin=student@example.com&from=2025-01-01T00:00:00Z&to=2025-12-31T23:59:59Z" \
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

### Получить прохождение по ID

```bash
curl http://localhost:8080/records/1 \
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
