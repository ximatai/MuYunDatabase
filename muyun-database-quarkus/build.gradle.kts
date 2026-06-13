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
    api(libs.quarkus.agroal)
    api(libs.quarkus.arc)
    api(libs.quarkus.narayana.jta)
    implementation(libs.quarkus.arc.api)
    implementation(libs.quarkus.core)
}

tasks.test {
    useJUnitPlatform()
}

val validateQuarkusExtensionDescriptor by tasks.registering {
    val descriptor = layout.projectDirectory.file("src/main/resources/META-INF/quarkus-extension.properties")
    inputs.file(descriptor)
    inputs.property("projectVersion", project.version.toString())

    doLast {
        val expectedArtifact = "net.ximatai.muyun.database:muyun-database-quarkus-deployment::jar:${project.version}"
        val expectedLine = "deployment-artifact=$expectedArtifact"
        val lines = descriptor.asFile.readLines()
        require(lines.contains(expectedLine)) {
            "Quarkus extension descriptor must contain '$expectedLine'."
        }
    }
}

tasks.named("processResources") {
    dependsOn(validateQuarkusExtensionDescriptor)
}
