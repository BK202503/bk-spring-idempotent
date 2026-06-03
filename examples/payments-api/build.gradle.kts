plugins {
    kotlin("jvm")
    kotlin("plugin.spring") version "2.0.21"
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

dependencies {
    implementation(project(":idempotent-spring-boot-starter"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.h2)
}
