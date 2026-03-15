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
| `make test` | Запустить все тесты |
| `make test-unit` | Только unit-тесты |
| `make test-integration` | Только интеграционные тесты |

## Тестирование

Требуется Docker (для Testcontainers — поднимает PostgreSQL автоматически).

```bash
# Запустить все тесты (98 тестов)
make test

# Только unit-тесты (сервисы + DAO)
make test-unit

# Только интеграционные тесты (HTTP-эндпоинты)
make test-integration
```

Тесты используют Testcontainers с реальным PostgreSQL, MockK для моков в unit-тестах сервисов, и Ktor `testApplication` для интеграционных тестов.

## Конфигурация

Файл `src/main/resources/application.conf` (HOCON). Все параметры переопределяются через переменные окружения:

| Параметр | Переменная окружения | По умолчанию |
|----------|---------------------|--------------|
| Порт сервера | `PORT` | `8080` |
| JWT secret | `JWT_SECRET` | dev-значение |
| URL базы данных | `DATABASE_URL` | `jdbc:postgresql://localhost:5432/eyetracker` |
| Пользователь БД | `DATABASE_USER` | `postgres` |
| Пароль БД | `DATABASE_PASSWORD` | `my-secret-pw` |

## Примеры запросов

Полные примеры curl-запросов с ответами — в [EXAMPLES.md](EXAMPLES.md).

## Деплой

Docker-окружение находится в директории `deploy/` (на основе [jvm-docker-env](https://github.com/dudosyka/jvm-docker-env)).

```bash
# Собрать fat jar и запустить в Docker
make deploy

# Остановить
make deploy-stop
```

Для production установите переменные окружения `JWT_SECRET`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`.

## Документация проекта

Подробная техническая документация — в [PROJECT.md](PROJECT.md).
