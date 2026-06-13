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
}

tasks.test {
    useJUnitPlatform()
}
