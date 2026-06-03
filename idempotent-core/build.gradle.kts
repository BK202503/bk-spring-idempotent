plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.slf4j.api)
    compileOnly(libs.micrometer.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
}

publishing {
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}
