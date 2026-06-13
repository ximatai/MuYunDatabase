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
    api(project(":muyun-database-jdbi"))
    implementation(platform(libs.quarkus.bom))
    implementation(libs.quarkus.core)
}

tasks.processResources {
    inputs.property("projectGroup", project.group.toString())
    inputs.property("projectVersion", project.version.toString())
    filesMatching("META-INF/quarkus-extension.properties") {
        expand(
            "projectGroup" to project.group.toString(),
            "projectVersion" to project.version.toString()
        )
    }
}

tasks.test {
    useJUnitPlatform()
}
