plugins {
    java
    alias(libs.plugins.quarkus)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(enforcedPlatform(libs.quarkus.bom))
    implementation(project(":muyun-database-quarkus"))
    implementation(libs.quarkus.jdbc.h2)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.quarkus.jdbc.postgresql)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.test {
    useJUnitPlatform()
    if (project.hasProperty("muyun.postgres.it.required")) {
        systemProperty("muyun.postgres.it.required", project.property("muyun.postgres.it.required").toString())
    }
}
