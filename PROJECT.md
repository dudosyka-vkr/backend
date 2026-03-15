# Документация проекта EyeTracker Backend

## Структура проекта

```
backend/
├── build.gradle.kts                           # Конфигурация Gradle, зависимости
├── settings.gradle.kts                        # Настройки Gradle проекта
├── gradle.properties                          # Kotlin code style
├── Makefile                                   # Команды: local, deploy, build, clean, test
├── src/main/kotlin/org/eyetracker/
│   ├── Application.kt                         # Точка входа (Netty EngineMain)
│   ├── auth/
│   │   ├── controller/
│   │   │   └── AuthController.kt              # POST /auth/login, /register, /users
│   │   ├── dao/
│   │   │   ├── UserTable.kt                   # Exposed IntIdTable("users")
│   │   │   ├── UserEntity.kt                  # Exposed IntEntity для users
│   │   │   └── UserDao.kt                     # findByLogin(), createUser()
│   │   ├── dto/
│   │   │   └── AuthDtos.kt                    # LoginRequest, RegisterRequest, CreateUserRequest,
│   │   │                                      # TokenResponse, UserResponse, ErrorResponse
│   │   └── service/
│   │       ├── JwtConfig.kt                   # Data class: secret, issuer, audience, realm
│   │       └── AuthService.kt                 # login(), register(), createUser(), Role object
│   ├── test/
│   │   ├── controller/
│   │   │   └── TestController.kt              # CRUD маршруты /tests (JWT-protected)
│   │   ├── dao/
│   │   │   ├── TestTable.kt                   # Exposed IntIdTable("tests")
│   │   │   ├── TestImageTable.kt              # Exposed IntIdTable("test_images")
│   │   │   ├── TestEntity.kt                  # Exposed IntEntity для tests
│   │   │   ├── TestImageEntity.kt             # Exposed IntEntity для test_images
│   │   │   └── TestDao.kt                     # create(), findAll(), findById(), deleteById()
│   │   ├── dto/
│   │   │   └── TestDtos.kt                    # TestResponse, TestListResponse, ErrorResponse
│   │   └── service/
│   │       └── TestService.kt                 # Бизнес-логика + файловый I/O
│   ├── record/
│   │   ├── controller/
│   │   │   └── RecordController.kt            # POST /records, GET /records, GET /records/{id}
│   │   ├── dao/
│   │   │   ├── RecordTable.kt                 # Exposed IntIdTable("records")
│   │   │   ├── RecordItemTable.kt             # Exposed IntIdTable("record_items")
│   │   │   ├── RecordEntity.kt                # Exposed IntEntity для records
│   │   │   ├── RecordItemEntity.kt            # Exposed IntEntity для record_items
│   │   │   └── RecordDao.kt                   # create(), findById(), findAll() с пагинацией
│   │   ├── dto/
│   │   │   └── RecordDtos.kt                  # CreateRecordRequest, RecordDetailResponse, RecordListResponse
│   │   └── service/
│   │       └── RecordService.kt               # Валидация, создание прохождений, пагинация
│   └── plugins/
│       ├── AppModule.kt                       # Koin DI-модуль (auth + test + record биндинги)
│       ├── DatabaseFactory.kt                 # Flyway миграции + Exposed Database.connect
│       ├── Koin.kt                            # Установка Koin плагина
│       ├── Routing.kt                         # Корневой роутинг (authRoutes + testRoutes + recordRoutes)
│       ├── Security.kt                        # JWT верификатор ("auth-jwt")
│       └── Serialization.kt                   # ContentNegotiation с kotlinx.json
├── src/main/resources/
│   ├── application.conf                       # HOCON конфигурация (ktor, jwt, storage, database)
│   ├── logback.xml                            # Настройки логирования
│   └── db/migration/
│       ├── V1__create_users_table.sql         # users(id, email, password_hash, created_at)
│       ├── V2__create_tests_tables.sql        # tests + test_images
│       ├── V3__add_role_to_users.sql          # Добавление колонки role в users
│       └── V4__create_records_tables.sql      # records + record_items
├── src/test/kotlin/org/eyetracker/
│   ├── base/
│   │   ├── DatabaseTestBase.kt                # Базовый класс для DAO-тестов (Testcontainers PG)
│   │   ├── IntegrationTestBase.kt             # Базовый класс для интеграционных тестов
│   │   └── TestFixtures.kt                    # Общие тестовые данные и фикстуры
│   ├── auth/
│   │   ├── dao/UserDaoTest.kt                 # 7 тестов UserDao
│   │   ├── service/AuthServiceTest.kt         # 13 тестов AuthService (MockK)
│   │   └── controller/
│   │       └── AuthControllerIntegrationTest.kt  # 18 интеграционных тестов
│   └── test/
│       ├── dao/TestDaoTest.kt                 # 10 тестов TestDao
│       ├── service/TestServiceTest.kt         # 21 тест TestService (MockK)
│       └── controller/
│           └── TestControllerIntegrationTest.kt  # 29 интеграционных тестов
├── src/test/resources/
│   └── application-test.conf                  # Тестовая конфигурация
├── deploy/                                    # Docker-окружение (docker-compose, postgres, jvm, nginx)
│   ├── docker-compose.yaml                    # Оркестрация контейнеров
│   ├── postgres/init.d/init.sql               # Создание БД eyetracker
│   └── jvm/home/startup.sh                    # Ожидание postgres + запуск jar
└── gradle/wrapper/                            # Gradle Wrapper
```

## Зависимости

| Компонент | Версия | Назначение |
|-----------|--------|------------|
| Kotlin | 2.1.10 | Язык |
| Ktor | 3.1.1 | HTTP-сервер (Netty) |
| Exposed | 0.58.0 | ORM |
| Koin | 4.0.2 | Dependency Injection |
| Flyway | 11.3.1 | Миграции БД |
| PostgreSQL Driver | 42.7.5 | JDBC-драйвер |
| kotlinx.serialization | — | JSON сериализация (через Ktor) |
| auth0 java-jwt | — | JWT токены (через ktor-server-auth-jwt) |
| jBCrypt | 0.4 | Хеширование паролей |
| Logback | 1.5.16 | Логирование |
| JVM | 21 | Рантайм |
| Gradle | 8.14 | Сборка |

### Тестовые зависимости

| Компонент | Версия | Назначение |
|-----------|--------|------------|
| Testcontainers | 1.20.4 | PostgreSQL в Docker для тестов |
| MockK | 1.13.14 | Мокирование в unit-тестах |
| Koin Test | 4.0.2 | Тестовая поддержка DI |
| Ktor Test Host | 3.1.1 | In-process HTTP тестирование |
| Ktor Client Content-Negotiation | 3.1.1 | JSON для тестового клиента |

---

## Архитектура

### Инициализация плагинов

Порядок установки плагинов в `Application.module()`:

```
1. configureKoin()              — DI первым, чтобы другие плагины могли инжектить
2. configureSerialization()     — JSON content negotiation
3. configureSecurity()          — JWT аутентификация
4. configureDatabaseFactory()   — Flyway миграции, затем Exposed connect
5. configureRouting()           — HTTP-маршруты
```

### Слоистая архитектура модулей

Каждый функциональный модуль (auth, test, record) следует трёхслойной архитектуре:

```
Controller (routes)  →  Service (бизнес-логика)  →  DAO (доступ к данным)
     ↑                        ↑                         ↑
  Только HTTP              Без роутинга            Все transaction{}
  request/response         Без БД напрямую          живут здесь
```

- **Controller** (`controller/`) — определяет маршруты, принимает/отдаёт DTO
- **Service** (`service/`) — бизнес-логика, валидация, файловый I/O
- **DAO** (`dao/`) — все вызовы `transaction {}` только здесь
- **DTO** (`dto/`) — `@Serializable` data-классы для запросов/ответов

### Dependency Injection (Koin)

DI-модуль создаётся через `Application.buildAppModule()` (не top-level val), потому что требуется доступ к `Application.environment` для чтения конфигурации.

Граф зависимостей:
```
AuthController (authRoutes) → AuthService → UserDao + JwtConfig
TestController (testRoutes) → TestService → TestDao + uploadDir (String)
RecordController (recordRoutes) → RecordService → RecordDao + TestDao
```

---

## Модуль аутентификации

### Роли

Система поддерживает три роли, определённые в объекте `Role`:

| Роль | Права |
|------|-------|
| `USER` | Чтение тестов (GET) |
| `ADMIN` | Чтение + создание/удаление тестов, создание пользователей с ролью USER |
| `SUPER_ADMIN` | Все права ADMIN + создание пользователей с ролью ADMIN |

Роль хранится в БД (`users.role`) и включается в JWT токен как claim `role`.

### JWT

- **Верификатор** в `Security.kt` — настраивает `authenticate("auth-jwt")` блок с HMAC256
- **Генерация токенов** в `AuthService.kt` — использует тот же алгоритм, issuer, audience
- Токен содержит claims: `userId` (Int), `email` (String), `role` (String), срок жизни — 1 час
- Защищённые маршруты оборачиваются в `authenticate("auth-jwt") { ... }`

### Хеширование паролей

BCrypt с автоматической генерацией salt (`BCrypt.gensalt()`). Проверка через `BCrypt.checkpw()`.

### AuthResult / CreateUserResult

Сервис возвращает sealed class вместо исключений:

```kotlin
sealed class AuthResult {
    data class Success(val token: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class CreateUserResult {
    data class Success(val id: Int, val login: String, val role: String) : CreateUserResult()
    data class Error(val message: String, val status: Int) : CreateUserResult()
}
```

Контроллер маппит результат в HTTP-ответ через `when`.

---

## Модуль тестов

### Описание

"Тест" — это именованная сущность с обложкой (cover image) и упорядоченным списком стимулирующих изображений. Тесты создаются администраторами и доступны для чтения всем аутентифицированным пользователям.

### Хранение файлов

Файлы хранятся на диске в настраиваемой директории:

```
{uploadDir}/tests/{testId}/
├── cover.{ext}
├── 000.{ext}
├── 001.{ext}
└── ...
```

Путь настраивается через `storage.uploadDir` в `application.conf` или переменную окружения `UPLOAD_DIR`. Директории создаются автоматически при первом запуске.

### TestResult

```kotlin
sealed class TestResult {
    data class Success(val response: TestResponse) : TestResult()
    data class Error(val message: String, val status: Int) : TestResult()
}
```

### Multipart загрузка

Создание и обновление теста (`POST /tests`, `PUT /tests/{id}`) принимают `multipart/form-data`:
- `name` (text) — название теста
- `cover` (file) — обложка
- `images` (file, повторяющееся) — стимулирующие изображения

Контроллер буферизирует байты файлов в память при парсинге multipart, затем передаёт `InputStream` в сервис для записи на диск.

### Обновление теста (PUT)

Обновление — это полная замена. Сервис записывает новые файлы во временную директорию (`tests/{id}_tmp`), затем удаляет старую директорию и переименовывает временную в финальную (атомарная замена). В БД удаляются старые записи `test_images` и вставляются новые.

---

## Модуль прохождений (Records)

### Описание

«Прохождение» (Record) — результат прохождения теста конкретным участником. Содержит временные метки начала/окончания, длительность и список элементов (RecordItem) с метриками по каждому изображению теста. Прохождения создаются клиентским приложением после завершения теста.

### Хранение метрик

Метрики каждого элемента хранятся как JSON-строка в колонке `metrics_json` (TEXT). Сериализация/десериализация через `kotlinx.serialization.json.Json`. Формат расширяем без миграций БД — достаточно обновить DTO `RecordItemMetrics`.

### RecordResult

```kotlin
sealed class RecordResult {
    data class Success(val response: RecordDetailResponse) : RecordResult()
    data class Error(val message: String, val status: Int) : RecordResult()
}
```

### Валидация при создании

- `userLogin` не может быть пустым → 400
- `items` не может быть пустым → 400
- `durationMs` должен быть ≥ 0 → 400
- `startedAt` / `finishedAt` должны быть валидными ISO-8601 → 400
- Тест с указанным `testId` должен существовать → 404
- Каждый `imageId` должен принадлежать указанному тесту → 400

---

## База данных

### Конфигурация

HOCON (`application.conf`) с переопределением через переменные окружения:

```hocon
database {
    url = "jdbc:postgresql://localhost:5432/eyetracker"
    url = ${?DATABASE_URL}
    user = "postgres"
    user = ${?DATABASE_USER}
    password = "my-secret-pw"
    password = ${?DATABASE_PASSWORD}
}

storage {
    uploadDir = "uploads"
    uploadDir = ${?UPLOAD_DIR}
}
```

### Миграции

Flyway запускается **до** подключения Exposed. Миграции лежат в `src/main/resources/db/migration/`.

| Версия | Описание |
|--------|----------|
| V1 | Таблица `users` (id, email, password_hash, created_at) |
| V2 | Таблицы `tests` (id, name, cover_filename, user_id, created_at) и `test_images` (id, test_id, filename, sort_order) с FK и каскадным удалением |
| V3 | Колонка `role` в таблице `users` (VARCHAR(20), DEFAULT 'USER') |
| V4 | Таблицы `records` (id, test_id, user_login, started_at, finished_at, duration_ms, created_at) и `record_items` (id, record_id, image_id, metrics_json) с FK и каскадным удалением |

### Схема БД

```
users
├── id SERIAL PK
├── email VARCHAR(255) UNIQUE
├── password_hash VARCHAR(255)
├── role VARCHAR(20) DEFAULT 'USER'
└── created_at TIMESTAMP

tests
├── id SERIAL PK
├── name VARCHAR(255)
├── cover_filename VARCHAR(255)
├── user_id INTEGER FK → users(id) ON DELETE CASCADE
└── created_at TIMESTAMP

test_images
├── id SERIAL PK
├── test_id INTEGER FK → tests(id) ON DELETE CASCADE
├── filename VARCHAR(255)
└── sort_order INTEGER

records
├── id SERIAL PK
├── test_id INTEGER FK → tests(id) ON DELETE CASCADE
├── user_login VARCHAR(255)
├── started_at TIMESTAMP
├── finished_at TIMESTAMP
├── duration_ms BIGINT
└── created_at TIMESTAMP

record_items
├── id SERIAL PK
├── record_id INTEGER FK → records(id) ON DELETE CASCADE
├── image_id INTEGER FK → test_images(id) ON DELETE CASCADE
└── metrics_json TEXT (JSON)
```

---

## API

### Аутентификация

| Метод | Путь | Аутентификация | Тело запроса | Ответ |
|-------|------|----------------|--------------|-------|
| POST | `/auth/register` | Нет | `{"login":"...","password":"..."}` | `201 {"token":"..."}` или `409 {"error":"..."}` |
| POST | `/auth/login` | Нет | `{"login":"...","password":"..."}` | `200 {"token":"..."}` или `401 {"error":"..."}` |
| POST | `/auth/users` | JWT (ADMIN+) | `{"login":"...","password":"...","role":"USER\|ADMIN"}` | `201 {"id":...,"login":"...","role":"..."}` или `403`/`409` |

Правила создания пользователей:
- SUPER_ADMIN может создавать ADMIN и USER
- ADMIN может создавать только USER
- Нельзя создать SUPER_ADMIN через API
- USER не может создавать пользователей (403)

### Тесты

| Метод | Путь | Аутентификация | Описание | Ответ |
|-------|------|----------------|----------|-------|
| POST | `/tests` | JWT (ADMIN+) | Создать тест (multipart: name, cover, images) | `201 TestResponse` |
| PUT | `/tests/{id}` | JWT (ADMIN+) | Обновить тест (multipart: name, cover, images — полная замена) | `200 TestResponse` или `404` |
| GET | `/tests` | JWT | Список всех тестов | `200 {"tests":[...]}` |
| GET | `/tests/{id}` | JWT | Получить тест по ID | `200 TestResponse` или `404` |
| DELETE | `/tests/{id}` | JWT (ADMIN+) | Удалить тест и файлы | `204` или `404` |
| GET | `/tests/{id}/cover` | JWT | Скачать обложку | `200` binary или `404` |
| GET | `/tests/{id}/images/{index}` | JWT | Скачать изображение по индексу (0-based) | `200` binary или `404` |

### Формат TestResponse

```json
{
    "id": 1,
    "name": "My Test",
    "coverUrl": "/tests/1/cover",
    "imageUrls": ["/tests/1/images/0", "/tests/1/images/1"],
    "imageIds": [1, 2],
    "createdAt": "2025-01-15T12:00:00Z"
}
```

### Прохождения (Records)

| Метод | Путь | Аутентификация | Описание | Ответ |
|-------|------|----------------|----------|-------|
| POST | `/records` | JWT | Создать прохождение с результатами по изображениям | `201 RecordDetailResponse` или `400`/`404` |
| GET | `/records` | JWT | Список прохождений (пагинация + фильтры) | `200 RecordListResponse` |
| GET | `/records/{id}` | JWT | Детали прохождения с items | `200 RecordDetailResponse` или `404` |

Query-параметры для `GET /records`:
- `page` (Int, default 1) — номер страницы
- `pageSize` (Int, default 20, max 100) — размер страницы
- `userLogin` (String?) — фильтр по логину участника
- `from` (ISO-8601 String?) — начало временного диапазона (по startedAt)
- `to` (ISO-8601 String?) — конец временного диапазона (по startedAt)

### Формат RecordDetailResponse

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

### Формат RecordListResponse

```json
{
    "items": [
        {"id": 1, "testId": 1, "userLogin": "student@example.com", "startedAt": "...", "finishedAt": "...", "durationMs": 300000, "createdAt": "..."}
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1
}
```

---

## Тестирование

### Обзор

152 теста, разделённых на три категории:

| Категория | Количество | Подход |
|-----------|------------|--------|
| Unit-тесты сервисов (AuthServiceTest, TestServiceTest, RecordServiceTest) | 51 | MockK для моков DAO, без БД |
| Тесты DAO (UserDaoTest, TestDaoTest, RecordDaoTest) | 31 | Testcontainers PostgreSQL, реальная БД |
| Интеграционные тесты (контроллеры Auth + Test + Record) | 70 | Полное приложение Ktor + Testcontainers, реальные HTTP-запросы |

### Инфраструктура тестов

**DatabaseTestBase** — базовый класс для DAO-тестов:
- Запускает Testcontainers PostgreSQL (один контейнер на класс)
- `@BeforeEach`: Flyway clean + migrate
- `@AfterEach`: TRUNCATE всех таблиц

**IntegrationTestBase** — базовый класс для интеграционных тестов:
- Запускает Testcontainers PostgreSQL
- Создаёт `testApplication` с `MapApplicationConfig`, указывающим на тестовую БД
- Предоставляет хелперы: `registerUser()`, `getAdminToken()`, `getSuperAdminToken()`, `createTestViaApi()`, `createRecordViaApi()`, `getImageIdsFromDb()`
- `@AfterEach`: очистка БД + удаление тестовых файлов

**TestFixtures** — общие тестовые данные:
- Константы логинов и паролей
- Минимальные валидные изображения (1x1 PNG, 1x1 JPEG) в виде байтовых массивов

### Покрытие

Unit-тесты покрывают все ветки бизнес-логики:
- `AuthService`: login (успех, не найден, неверный пароль), register (успех, дубликат), createUser (все комбинации ролей)
- `TestService`: create (успех, пустое имя, нет изображений, ошибка I/O), getAll, getById, update, delete, getCoverFile, getImageFile (все граничные случаи)
- `RecordService`: create (валидация: blank login, empty items, negative duration, invalid timestamp, test not found, invalid imageId), create success, getById, getAll (пагинация, clamping)

Интеграционные тесты проверяют:
- HTTP-статусы и тела ответов
- Авторизацию (отсутствие токена, невалидный токен, недостаточные права)
- Загрузку и скачивание файлов через multipart
- Полный жизненный цикл: создание → список → получение → скачивание → удаление → проверка удаления
- Создание прохождений, пагинацию, фильтрацию по userLogin и временному диапазону

### Запуск тестов

```bash
# Все тесты (требуется Docker для Testcontainers)
make test

# Только unit-тесты
make test-unit

# Только интеграционные тесты
make test-integration
```

---

## Docker / Деплой

### Директория `deploy/`

Docker-окружение находится в `deploy/` (на основе https://github.com/dudosyka/jvm-docker-env.git). Содержит docker-compose с postgres, jvm, nginx.

Ключевые файлы:
- `postgres/init.d/init.sql` — создание БД `eyetracker`
- `jvm/home/startup.sh` — ожидание postgres перед запуском jar
- `docker-compose.yaml` — переменные окружения `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`

### Makefile

| Команда | Описание |
|---------|----------|
| `make local` | Postgres в Docker + приложение через `./gradlew run` |
| `make local-db` | Только postgres контейнер |
| `make local-db-stop` | Остановка postgres контейнера |
| `make deploy` | Сборка fat jar, копирование в `deploy/jvm/dist/app.jar`, запуск postgres+jvm в Docker |
| `make deploy-stop` | Остановка всех контейнеров |
| `make build` | Только сборка fat jar |
| `make clean` | Очистка артефактов сборки + удаление jar из dist |
| `make test` | Запуск всех тестов (152 теста, требуется Docker) |
| `make test-unit` | Только unit-тесты (сервисы + DAO) |
| `make test-integration` | Только интеграционные тесты (контроллеры) |

### Переменные окружения

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `PORT` | `8080` | Порт HTTP-сервера |
| `JWT_SECRET` | dev-значение | Секрет для подписи JWT |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/eyetracker` | JDBC URL |
| `DATABASE_USER` | `postgres` | Пользователь БД |
| `DATABASE_PASSWORD` | `my-secret-pw` | Пароль БД |
| `UPLOAD_DIR` | `uploads` | Директория для загрузки файлов |
