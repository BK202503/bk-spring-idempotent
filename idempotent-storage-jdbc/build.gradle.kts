import java.io.File

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":idempotent-core"))
    api(libs.jackson.module.kotlin)
    api(libs.jackson.datatype.jsr310)
    implementation(libs.slf4j.api)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.h2)
    testImplementation(libs.hikari)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testRuntimeOnly(libs.postgres.driver)
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}

tasks.test {
    if (System.getenv("DOCKER_HOST") == null) {
        val candidates = listOf(
            "${System.getProperty("user.home")}/.docker/run/docker.sock",
            "${System.getProperty("user.home")}/.colima/default/docker.sock",
            "/var/run/docker.sock",
        )
        candidates.firstOrNull { File(it).exists() }?.let { sock ->
            environment("DOCKER_HOST", "unix://$sock")
        }
    }
}
