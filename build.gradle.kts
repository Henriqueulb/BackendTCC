plugins {
    // Usamos apenas plugins padrão do Kotlin, sem o plugin do Ktor que causa erro
    kotlin("jvm") version "1.9.23"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
    application // Plugin padrão para rodar apps Java/Kotlin
}

group = "com.exemplo.medico"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    // Definicoes de versoes fixas
    val ktor_version = "2.3.9"
    val logback_version = "1.4.14"
    val exposed_version = "0.41.1"
    val postgres_version = "42.6.0"
    val hikari_version = "5.0.1"

    // --- SERVIDOR KTOR ---
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // --- BANCO DE DADOS ---
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
    implementation("at.favre.lib:bcrypt:0.10.2")
}

application {
    // Aponta para o arquivo Application.kt
    mainClass.set("com.exemplo.medico.ApplicationKt")
}

// Configuracao para garantir compatibilidade do Kotlin
kotlin {
    jvmToolchain(17) // Garante que use Java 17 (padrão atual)
}