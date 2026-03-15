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
