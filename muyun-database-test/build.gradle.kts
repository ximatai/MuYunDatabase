plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    testImplementation(project(":muyun-database-jdbi"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.hikariCP)
    testImplementation(libs.postgresql)
    testImplementation(libs.mysql)
    testImplementation(libs.bundles.testcontainers.all)
    testImplementation(libs.commons.compress)

    testImplementation(libs.logback.classic)
}
