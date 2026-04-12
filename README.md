# EyeTracker Backend

REST API сервер для проекта EyeTracker. Построен на Ktor (Kotlin).

## Требования

- JDK 21+
- Docker и Docker Compose (для PostgreSQL)

## Быстрый старт

```bash
# Поднять postgres + запустить приложение
make local
```

Сервер стартует на `http://localhost:8080`.

## Makefile команды

| Команда | Описание |
|---------|----------|
| `make local` | Postgres в Docker + приложение локально |
| `make local-db` | Только postgres контейнер |
| `make local-db-stop` | Остановить postgres |
| `make deploy` | Сборка fat jar + запуск в Docker (postgres + jvm) |
| `make deploy-stop` | Остановить все контейнеры |
| `make build` | Собрать fat jar |
| `make clean` | Очистить артефакты сборки |

## Конфигурация

Файл `src/main/resources/application.conf` (HOCON). Все параметры переопределяются через переменные окружения:

| Параметр | Переменная окружения | По умолчанию |
|----------|---------------------|--------------|
| Порт сервера | `PORT` | `8080` |
| JWT secret | `JWT_SECRET` | dev-значение |
| URL базы данных | `DATABASE_URL` | `jdbc:postgresql://localhost:5432/eyetracker` |
| Пользователь БД | `DATABASE_USER` | `postgres` |
| Пароль БД | `DATABASE_PASSWORD` | `my-secret-pw` |
| Директория загрузок | `UPLOAD_DIR` | `uploads` |

## Документация

- Полная техническая документация — [PROJECT.md](PROJECT.md)
- Все маршруты API с форматами запросов и ответов — [API_DOC.md](API_DOC.md)
- Примеры curl-запросов — [EXAMPLES.md](EXAMPLES.md)

## Деплой

Docker-окружение находится в директории `deploy/`.

```bash
# Собрать fat jar и запустить в Docker
make deploy

# Остановить
make deploy-stop
```

Для production установите переменные окружения `JWT_SECRET`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`.
