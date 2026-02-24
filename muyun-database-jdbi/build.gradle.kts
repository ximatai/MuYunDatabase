plugins {
    java
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    api(project(":muyun-database-core"))
    api(libs.jdbi3.core)
    api(libs.jdbi3.sqlobject)
    api(libs.jdbi3.jackson2)
    api(libs.jdbi3.postgres)
}
