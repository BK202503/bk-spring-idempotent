plugins {
    kotlin("jvm") version "2.0.21" apply false
    `maven-publish`
}

allprojects {
    group = "io.github.idempotentkt"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    repositories { mavenCentral() }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict", "-Xjvm-default=all")
            }
        }
        tasks.withType<Test> { useJUnitPlatform() }
    }
}
