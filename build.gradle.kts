val ktor_version = "3.1.1"
val exposed_version = "0.58.0"
val logback_version = "1.5.16"
val koin_version = "4.0.2"
val flyway_version = "9.22.3"

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("io.ktor.plugin") version "3.1.1"
}

group = "org.eyetracker"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("org.eyetracker.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")

    // Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed_version")

    // PostgreSQL
    implementation("org.postgresql:postgresql:42.7.5")

    // Flyway Migrations
    implementation("org.flywaydb:flyway-core:$flyway_version")

    // Koin DI
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")

    // BCrypt
    implementation("org.mindrot:jbcrypt:0.4")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    testImplementation("org.testcontainers:testcontainers:1.21.0")
    testImplementation("org.testcontainers:postgresql:1.21.0")
    testImplementation("org.testcontainers:junit-jupiter:1.21.0")
    testImplementation("io.insert-koin:koin-test:$koin_version")
    testImplementation("io.insert-koin:koin-test-junit5:$koin_version")
    testImplementation("io.mockk:mockk:1.13.14")
}

tasks.test {
    useJUnitPlatform()
    val colimaSocket = "${System.getProperty("user.home")}/.colima/default/docker.sock"
    if (File(colimaSocket).exists()) {
        environment("DOCKER_HOST", "unix://$colimaSocket")
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", colimaSocket)
        environment("DOCKER_API_VERSION", "1.44")
    }
}

kotlin {
    jvmToolchain(21)
}
