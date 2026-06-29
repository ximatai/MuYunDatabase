buildscript {
    configurations.classpath {
        resolutionStrategy.force("org.codehaus.plexus:plexus-utils:4.0.3")
    }
}

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
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.rest)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.quarkus.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.plexus.utils)
}

configurations.configureEach {
    resolutionStrategy.force(libs.plexus.utils.get().toString())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    if (project.hasProperty("muyun.postgres.it.required")) {
        systemProperty("muyun.postgres.it.required", project.property("muyun.postgres.it.required").toString())
    }
    listOf(
        "muyun.native.postgres.enabled",
        "quarkus.datasource.db-kind",
        "quarkus.datasource.jdbc.url",
        "quarkus.datasource.username",
        "quarkus.datasource.password",
        "muyun.database.default-schema",
        "muyun.database.install-postgres-plugins"
    ).forEach { name ->
        System.getProperty(name)?.let { value ->
            systemProperty(name, value)
        }
    }
}
