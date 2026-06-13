plugins {
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
    implementation(project(":muyun-database-quarkus"))
    implementation(platform(libs.quarkus.bom))
    implementation(libs.quarkus.core.deployment)
}

tasks.test {
    useJUnitPlatform()
}
