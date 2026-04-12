# Документация проекта EyeTracker Backend

## Структура проекта

```
backend/
├── build.gradle.kts                           # Конфигурация Gradle, зависимости
├── settings.gradle.kts                        # Настройки Gradle проекта
├── gradle.properties                          # Kotlin code style
├── Makefile                                   # Команды: local, deploy, build, clean
├── src/main/kotlin/org/eyetracker/
│   ├── Application.kt                         # Точка входа (Netty EngineMain)
│   ├── auth/
│   │   ├── controller/
│   │   │   └── AuthController.kt              # POST /auth/login, /register, /users; GET /auth/me/role
│   │   ├── dao/
│   │   │   ├── UserTable.kt                   # Exposed IntIdTable("users")
│   │   │   ├── UserEntity.kt                  # Exposed IntEntity для users
│   │   │   └── UserDao.kt                     # findByLogin(), createUser()
│   │   ├── dto/
│   │   │   └── AuthDtos.kt                    # LoginRequest, RegisterRequest, CreateUserRequest,
│   │   │                                      # TokenResponse, UserResponse, RoleResponse, ErrorResponse
│   │   └── service/
│   │       ├── JwtConfig.kt                   # Data class: secret, issuer, audience, realm
│   │       └── AuthService.kt                 # login(), register(), createUser(), Role object
│   ├── test/
│   │   ├── controller/
│   │   │   └── TestController.kt              # Маршруты /tests и /tests/by-token/{code}
│   │   ├── dao/
│   │   │   ├── TestTable.kt                   # Exposed IntIdTable("tests")
│   │   │   ├── TestEntity.kt                  # Exposed IntEntity для tests
│   │   │   ├── TestDao.kt                     # create(), findAll(), findById(), updateName(), updateAoi(), deleteById()
│   │   │   ├── TestPassTokenTable.kt          # Exposed IntIdTable("test_pass_tokens")
│   │   │   ├── TestPassTokenEntity.kt         # Exposed IntEntity для test_pass_tokens
│   │   │   └── TestPassTokenDao.kt            # findByTestId(), findByCode(), create()
│   │   ├── dto/
│   │   │   └── TestDtos.kt                    # TestResponse, TestListResponse, UpdateAoiRequest,
│   │   │                                      # UpdateAoiResponse, TestPassTokenResponse,
│   │   │                                      # AoiStatEntry, AoiStatsResponse, ErrorResponse
│   │   └── service/
│   │       └── TestService.kt                 # Бизнес-логика + файловый I/O + AOI-статистика
│   ├── record/
│   │   ├── controller/
│   │   │   └── RecordController.kt            # POST /records/unauthorized; маршруты /records
│   │   ├── dao/
│   │   │   ├── RecordTable.kt                 # Exposed IntIdTable("records")
│   │   │   ├── RecordEntity.kt                # Exposed IntEntity для records
│   │   │   └── RecordDao.kt                   # create(), findById(), findAll(), findAllForTest(),
│   │   │                                      # findAllUnpaginated(), updateMetrics(), suggestUsers()
│   │   ├── dto/
│   │   │   └── RecordDtos.kt                  # CreateRecordRequest, CreateUnauthorizedRecordRequest,
│   │   │                                      # RecordResponse, RecordListResponse,
│   │   │                                      # RecordItemMetrics, UserSuggestResponse,
│   │   │                                      # AoiSyncResponse, ErrorResponse
│   │   └── service/
│   │       ├── RecordService.kt               # Валидация, создание прохождений, AOI-фильтрация
│   │       └── RoiUtils.kt                    # computeRoiMetrics(), pointInPolygon()
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
│       ├── V2__create_tests_tables.sql        # Старые таблицы tests + test_images (устарело)
│       ├── V3__add_role_to_users.sql          # Добавление колонки role в users
│       ├── V4__create_records_tables.sql      # Старые таблицы records + record_items (устарело)
│       ├── V5__...sql                         # Исторические миграции
│       ├── V6__...sql                         # Исторические миграции
│       ├── V7__create_one_image_tests.sql     # Создание one_image_tests
│       ├── V8__create_one_image_test_records.sql  # Создание one_image_test_records
│       ├── V9__add_aoi_to_one_image_test.sql  # Колонка aoi
│       ├── V10__add_pass_tokens.sql           # Таблица токенов
│       ├── V11__add_aoi_metrics.sql           # Дополнительные поля метрик
│       └── V12__rename_tables.sql             # Финальное переименование в tests/records/test_pass_tokens
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
AuthController  → AuthService → UserDao + JwtConfig
TestController  → TestService → TestDao + TestPassTokenDao + RecordDao + uploadDir (String)
RecordController → RecordService → RecordDao + TestDao + TestPassTokenDao
```

---

## Модуль аутентификации

### Роли

Система поддерживает три роли, определённые в объекте `Role`:

| Роль | Права |
|------|-------|
| `USER` | Чтение тестов (GET), создание прохождений |
| `ADMIN` | Всё USER + создание/удаление тестов, управление токенами, создание пользователей с ролью USER |
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

---

## Модуль тестов

### Описание

«Тест» — именованная сущность с одним изображением и набором AOI (Areas of Interest). Тесты создаются администраторами. Любой аутентифицированный пользователь может читать тесты и получать изображения. Для неаутентифицированных клиентов доступен поиск теста по токену прохождения.

### Хранение файлов

```
{uploadDir}/tests/{testId}/image.{ext}
```

Путь настраивается через `storage.uploadDir` в `application.conf` или переменную окружения `UPLOAD_DIR`. Директория создаётся автоматически при создании теста.

### Токены прохождения

Каждый тест может иметь уникальный 8-значный числовой токен (`TestPassTokenTable`). Токен позволяет неаутентифицированным клиентам находить тест и отправлять прохождения. Генерируется при первом запросе через `POST /tests/{id}/token` и повторно используется при последующих запросах.

### TestResult / AoiStatsResult

```kotlin
sealed class TestResult {
    data class Success(val response: TestResponse) : TestResult()
    data class Error(val message: String, val status: Int) : TestResult()
}

sealed class AoiStatsResult {
    data class Success(val response: AoiStatsResponse) : AoiStatsResult()
    data class Error(val message: String, val status: Int) : AoiStatsResult()
}
```

### Multipart загрузка

Создание теста (`POST /tests`) принимает `multipart/form-data`:
- `name` (text) — название теста
- `image` (file) — изображение теста

Контроллер буферизирует байты файла в память при парсинге multipart, затем передаёт `InputStream` в сервис для записи на диск.

### AOI (Areas of Interest)

AOI хранится в колонке `aoi` (TEXT, nullable) таблицы `tests` как JSON-массив объектов. Обновляется через `PATCH /tests/{id}/aoi`. Используется сервисом прохождений для пересчёта метрик через `computeRoiMetrics()` из `RoiUtils.kt`.

---

## Модуль прохождений (Records)

### Описание

«Прохождение» (Record) — результат просмотра теста конкретным участником. Содержит временные метки начала/окончания, длительность и метрики в формате JSON. Прохождения создаются клиентским приложением после завершения теста — либо аутентифицированным пользователем, либо анонимно по токену прохождения.

### Хранение метрик

Метрики хранятся как JSON-строка в колонке `metrics_json` (TEXT). Сериализация/десериализация через `kotlinx.serialization`. Структура расширяема без миграций БД.

Формат `RecordItemMetrics`:
```kotlin
@Serializable
data class RecordItemMetrics(
    val fixations: List<...> = emptyList(),
    val roiMetrics: List<JsonObject> = emptyList(),
    // ... другие поля метрик
)
```

### AOI-фильтрация

`GET /records` поддерживает фильтрацию по AOI через query-параметры вида `aoi.<name>=true|false`. При наличии AOI-фильтра сервис загружает все записи без пагинации, фильтрует в памяти, затем применяет пагинацию к результату.

### Пересчёт AOI-метрик

`POST /records/sync-aoi?testId=N` — пересчитывает `roiMetrics` для всех прохождений теста на основе текущего AOI теста. Использует `computeRoiMetrics()` из `RoiUtils.kt` (ray-casting point-in-polygon).

`GET /records/aoi-sync?testId=N` — проверяет, сколько прохождений не синхронизированы с текущим AOI теста (сравнивает набор имён AOI в метриках с набором имён в тесте).

### RecordResult

```kotlin
sealed class RecordResult {
    data class Success(val response: RecordResponse) : RecordResult()
    data class Error(val message: String, val status: Int) : RecordResult()
}
```

### Валидация при создании

- `durationMs` должен быть ≥ 0 → 400
- `startedAt` / `finishedAt` должны быть валидными ISO-8601 → 400
- Тест с указанным `testId` должен существовать → 404
- При создании по токену: токен должен существовать → 404; `login` обязателен → 400

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
| V2 | Исторические таблицы (удалены в V12) |
| V3 | Колонка `role` в таблице `users` (VARCHAR(20), DEFAULT 'USER') |
| V4 | Исторические таблицы (удалены в V12) |
| V5–V11 | Промежуточные миграции (one_image_tests, one_image_test_records, aoi, pass_tokens) |
| V12 | Финальное переименование: `one_image_tests` → `tests`, `one_image_test_records` → `records`, `one_image_test_pass_tokens` → `test_pass_tokens`; удаление старых таблиц |

### Схема БД (актуальная)

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
├── image VARCHAR(255)
├── aoi TEXT NULLABLE
├── user_id INTEGER FK → users(id)
└── created_at TIMESTAMP

test_pass_tokens
├── id SERIAL PK
├── test_id INTEGER FK → tests(id)
└── code VARCHAR(8) UNIQUE

records
├── id SERIAL PK
├── test_id INTEGER FK → tests(id)
├── user_login VARCHAR(255)
├── metrics_json TEXT (JSON)
├── started_at TIMESTAMP
├── finished_at TIMESTAMP
├── duration_ms BIGINT
└── created_at TIMESTAMP
```

---

## API

Полная документация маршрутов с форматами запросов и ответов — [API_DOC.md](API_DOC.md).

### Аутентификация

| Метод | Путь | Аутентификация | Описание |
|-------|------|----------------|----------|
| POST | `/auth/register` | Нет | Регистрация, возвращает JWT |
| POST | `/auth/login` | Нет | Вход, возвращает JWT |
| GET | `/auth/me/role` | JWT | Роль текущего пользователя |
| POST | `/auth/users` | JWT (ADMIN+) | Создание пользователя |

### Тесты

| Метод | Путь | Аутентификация | Описание |
|-------|------|----------------|----------|
| GET | `/tests/by-token/{code}` | Нет | Получить тест по токену прохождения |
| POST | `/tests` | JWT (ADMIN+) | Создать тест (multipart: name, image) |
| PATCH | `/tests/{id}/name` | JWT (ADMIN+) | Переименовать тест |
| DELETE | `/tests/{id}` | JWT (ADMIN+) | Удалить тест и файлы |
| POST | `/tests/{id}/token` | JWT (ADMIN+) | Получить или создать токен прохождения |
| GET | `/tests/{id}/aoi-stats` | JWT (ADMIN+) | AOI-статистика по тесту |
| PATCH | `/tests/{id}/aoi` | JWT (ADMIN+) | Обновить AOI теста |
| GET | `/tests` | JWT | Список тестов (фильтр по name) |
| GET | `/tests/{id}` | JWT | Получить тест по ID |
| GET | `/tests/{id}/image` | JWT | Скачать изображение теста |

### Прохождения

| Метод | Путь | Аутентификация | Описание |
|-------|------|----------------|----------|
| POST | `/records/unauthorized` | Нет | Создать прохождение по токену |
| POST | `/records` | JWT | Создать прохождение |
| GET | `/records` | JWT | Список прохождений (пагинация + фильтры) |
| GET | `/records/users/suggest` | JWT | Уникальные логины участников |
| GET | `/records/aoi-sync` | JWT | Проверить синхронизацию AOI-метрик |
| POST | `/records/sync-aoi` | JWT | Пересчитать AOI-метрики |
| GET | `/records/{id}` | JWT | Получить прохождение по ID |

---

## Docker / Деплой

### Директория `deploy/`

Docker-окружение находится в `deploy/`. Содержит docker-compose с postgres, jvm, nginx.

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

### Переменные окружения

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `PORT` | `8080` | Порт HTTP-сервера |
| `JWT_SECRET` | dev-значение | Секрет для подписи JWT |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/eyetracker` | JDBC URL |
| `DATABASE_USER` | `postgres` | Пользователь БД |
| `DATABASE_PASSWORD` | `my-secret-pw` | Пароль БД |
| `UPLOAD_DIR` | `uploads` | Директория для загрузки файлов |
