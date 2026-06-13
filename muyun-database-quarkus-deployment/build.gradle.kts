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
    implementation(libs.quarkus.agroal.deployment)
    implementation(libs.quarkus.arc.deployment)
    implementation(libs.quarkus.core.deployment)
    implementation(libs.quarkus.narayana.jta.deployment)
    annotationProcessor(libs.quarkus.extension.processor)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
