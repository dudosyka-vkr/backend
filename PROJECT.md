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
│   │   │   └── AuthController.kt              # POST /auth/login, POST /auth/register
│   │   ├── dao/
│   │   │   ├── UserTable.kt                   # Exposed IntIdTable("users")
│   │   │   ├── UserEntity.kt                  # Exposed IntEntity для users
│   │   │   └── UserDao.kt                     # findByLogin(), createUser()
│   │   ├── dto/
│   │   │   └── AuthDtos.kt                    # LoginRequest, RegisterRequest, TokenResponse, ErrorResponse
│   │   └── service/
│   │       ├── JwtConfig.kt                   # Data class: secret, issuer, audience, realm
│   │       └── AuthService.kt                 # login(), register(), generateToken()
│   └── plugins/
│       ├── AppModule.kt                       # Koin DI-модуль (биндинги зависимостей)
│       ├── DatabaseFactory.kt                 # Flyway миграции + Exposed Database.connect
│       ├── Koin.kt                            # Установка Koin плагина
│       ├── Routing.kt                         # Корневой роутинг + монтирование authRoutes
│       ├── Security.kt                        # JWT верификатор ("auth-jwt")
│       └── Serialization.kt                   # ContentNegotiation с kotlinx.json
├── src/main/resources/
│   ├── application.conf                       # HOCON конфигурация (ktor, jwt, database)
│   ├── logback.xml                            # Настройки логирования
│   └── db/migration/
│       └── V1__create_users_table.sql         # users(id, email, password_hash, created_at)
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

Каждый функциональный модуль (auth, и далее) следует трёхслойной архитектуре:

```
Controller (routes)  →  Service (бизнес-логика)  →  DAO (доступ к данным)
     ↑                        ↑                         ↑
  Только HTTP              Без роутинга            Все transaction{}
  request/response         Без БД напрямую          живут здесь
```

- **Controller** (`controller/`) — определяет маршруты, принимает/отдаёт DTO
- **Service** (`service/`) — бизнес-логика, валидация, генерация токенов
- **DAO** (`dao/`) — все вызовы `transaction {}` только здесь
- **DTO** (`dto/`) — `@Serializable` data-классы для запросов/ответов

### Dependency Injection (Koin)

DI-модуль создаётся через `Application.buildAppModule()` (не top-level val), потому что требуется доступ к `Application.environment` для чтения конфигурации:

```kotlin
// AppModule.kt
fun Application.buildAppModule() = module {
    single { JwtConfig(/* из application.conf */) }
    single { UserDao() }
    single { AuthService(get(), get()) }
}
```

Граф зависимостей:
```
AuthController (authRoutes) → AuthService → UserDao
                             → JwtConfig
```

---

## Модуль аутентификации

### JWT

- **Верификатор** в `Security.kt` — настраивает `authenticate("auth-jwt")` блок с HMAC256
- **Генерация токенов** в `AuthService.kt` — использует тот же алгоритм, issuer, audience
- Токен содержит claims: `userId` (Int), `email` (String), срок жизни — 1 час
- Защищённые маршруты оборачиваются в `authenticate("auth-jwt") { ... }`

### Хеширование паролей

BCrypt с автоматической генерацией salt (`BCrypt.gensalt()`). Проверка через `BCrypt.checkpw()`.

### AuthResult

Сервис возвращает sealed class вместо исключений:

```kotlin
sealed class AuthResult {
    data class Success(val token: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
```

Контроллер маппит результат в HTTP-ответ через `when`.

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
```

### Миграции

Flyway запускается **до** подключения Exposed. Миграции лежат в `src/main/resources/db/migration/` с именованием `V{N}__{description}.sql`.

Текущие миграции:

| Версия | Описание |
|--------|----------|
| V1 | Создание таблицы `users` (id, email, password_hash, created_at) |

### Exposed ORM

Таблица описана как `IntIdTable`, сущность — как `IntEntity`:

```kotlin
object UserTable : IntIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at")
}
```

---

## API

| Метод | Путь | Аутентификация | Тело запроса | Ответ |
|-------|------|----------------|--------------|-------|
| GET | `/` | Нет | — | `"Hello, Ktor!"` |
| POST | `/auth/register` | Нет | `{"login":"...","password":"..."}` | `201 {"token":"..."}` или `409 {"error":"..."}` |
| POST | `/auth/login` | Нет | `{"login":"...","password":"..."}` | `200 {"token":"..."}` или `401 {"error":"..."}` |

Примеры curl-запросов с полными ответами — в [EXAMPLES.md](EXAMPLES.md).

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
| `make deploy` | Сборка fat jar (shadowJar), копирование в `deploy/jvm/dist/app.jar`, запуск postgres+jvm в Docker |
| `make deploy-stop` | Остановка всех контейнеров |
| `make build` | Только сборка fat jar |
| `make clean` | Очистка артефактов сборки + удаление jar из dist |
