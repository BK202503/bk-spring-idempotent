plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":idempotent-core"))
    api(project(":idempotent-storage-jdbc"))
    api(libs.spring.boot.starter)
    api(libs.spring.boot.autoconfigure)
    api(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.micrometer.core)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.h2)
    testImplementation(libs.kotest.assertions)
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}
