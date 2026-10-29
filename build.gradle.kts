plugins {
    java
    checkstyle
    signing
    id("io.github.jeadyx.sonatype-uploader") version "2.8"
}

import java.util.Base64

checkstyle {
    toolVersion = "13.7.0"
}

configurations.named("checkstyle") {
    resolutionStrategy.force(
        "org.apache.commons:commons-lang3:3.20.0",
        "org.codehaus.plexus:plexus-utils:4.0.3"
    )
}

val releasePublishModules = listOf(
    "muyun-database-core",
    "muyun-database-core-json-jackson",
    "muyun-database-jdbi",
    "muyun-database-spring-boot-starter",
    "muyun-database-quarkus",
    "muyun-database-quarkus-deployment"
)

fun Project.releaseValue(propertyName: String, environmentName: String): String? {
    return findProperty(propertyName)
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != "null" }
        ?: providers.environmentVariable(environmentName)
            .orNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

fun Project.releaseSigningSecretKey(): String? {
    releaseValue("signing.secretKey", "SIGNING_SECRET_KEY")?.let { return it }

    return releaseValue("signing.secretKeyBase64", "SIGNING_SECRET_KEY_BASE64")?.let { encoded ->
        String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    }
}

val mavenCentralReleaseMode = providers.gradleProperty("muyun.mavenCentralRelease").map(String::toBoolean).orElse(false)

fun Project.releaseSigningCredentials(): Map<String, String?> {
    return mapOf(
        "signing.keyId or SIGNING_KEY_ID" to releaseValue("signing.keyId", "SIGNING_KEY_ID"),
        "signing.secretKey/signing.secretKeyBase64 or SIGNING_SECRET_KEY/SIGNING_SECRET_KEY_BASE64" to
                releaseSigningSecretKey(),
        "signing.password or SIGNING_PASSWORD" to releaseValue("signing.password", "SIGNING_PASSWORD")
    )
}

fun Project.requireReleaseCredentials() {
    val missing = (releaseSigningCredentials() + mapOf(
        "SONATYPE_TOKEN" to releaseValue("sonatype.token", "SONATYPE_TOKEN"),
        "SONATYPE_PASSWORD" to releaseValue("sonatype.password", "SONATYPE_PASSWORD")
    )).filterValues { it.isNullOrBlank() }.keys
    require(missing.isEmpty()) {
        "Missing required Gradle properties for Sonatype publish: ${missing.joinToString(", ")}"
    }
}

allprojects {
    group = "net.ximatai.muyun.database"
//    version = "1.0.0-SNAPSHOT"
    version = "3.26.25"

    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/repository/maven") }
        mavenCentral()
    }
}

tasks.register("verifyReleaseCredentials") {
    group = "verification"
    description = "Verify that all Sonatype and signing credentials are available."

    doLast {
        requireReleaseCredentials()
    }
}

tasks.register("verifyReleaseTagVersion") {
    group = "verification"
    description = "Verify that the release tag matches the Gradle project version."

    doLast {
        val tag = releaseValue("release.tag", "GITHUB_REF_NAME")
            ?: error("Missing release tag. Provide -Prelease.tag=v${project.version} or set GITHUB_REF_NAME.")
        val expectedTag = "v${project.version}"
        require(tag == expectedTag) {
            "Release tag '$tag' does not match project version '${project.version}'. Expected '$expectedTag'."
        }
    }
}

tasks.register("verifyMavenCentralReleaseMode") {
    doLast {
        require(mavenCentralReleaseMode.get()) { "Maven Central staging requires -Pmuyun.mavenCentralRelease=true" }
    }
}

tasks.register("verifyReleaseSigningCredentials") {
    doLast {
        val missing = releaseSigningCredentials().filterValues { it.isNullOrBlank() }.keys
        require(missing.isEmpty()) { "Missing signing credentials: ${missing.joinToString()}" }
    }
}

val centralRepository = layout.buildDirectory.dir("maven-central/repository")
val centralCoordinates = project.group.toString().replace('.', '/')

tasks.register<Sync>("stageMavenCentralRelease") {
    group = "publishing"
    description = "Stages signed public artifacts for Maven Central without uploading them."
    dependsOn("verifyMavenCentralReleaseMode")
    dependsOn("verifyReleaseSigningCredentials")
    dependsOn("verifyReleaseTagVersion")
    dependsOn(releasePublishModules.map { ":$it:publishAllPublicationsToSonatypeStagingRepository" })
    into(centralRepository)
    duplicatesStrategy = DuplicatesStrategy.FAIL
    releasePublishModules.forEach { artifact ->
        val coordinate = "$centralCoordinates/$artifact/${project.version}"
        from(project(":$artifact").layout.buildDirectory.dir("repo/$coordinate")) {
            into(coordinate)
        }
    }
}

tasks.register("publishReleaseToLocalRepository") {
    group = "publishing"
    description = "Publishes unsigned release modules to the local consumer repository."
    dependsOn("publishReleaseToConsumerRepository")
}

tasks.register("publishReleaseToConsumerRepository") {
    group = "publishing"
    description = "Publishes all public artifacts to one repository consumable by an external sample application."
    dependsOn(releasePublishModules.map { ":$it:publishAllPublicationsToConsumerRepository" })
}

tasks.register<Exec>("verifyPublishedConsumers") {
    group = "verification"
    description = "Builds Spring Boot and Quarkus samples using only the local published coordinates."
    dependsOn("publishReleaseToConsumerRepository")
    commandLine("bash", "scripts/verify-published-consumers.sh", project.version.toString())
}

tasks.register<Exec>("reportMavenPublication") {
    group = "publishing"
    description = "Reports the current unsigned Maven consumer publication without contacting a registry."
    dependsOn("publishReleaseToConsumerRepository")
    commandLine("node", "scripts/maven-publication-report.mjs",
        layout.buildDirectory.dir("consumer-repo").get().asFile.path,
        project.group.toString(), project.version.toString(), releasePublishModules.joinToString(","),
        "unsigned", layout.buildDirectory.file("maven-publication-report.json").get().asFile.path)
}

tasks.register<Exec>("verifyMavenCentralBundle") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies the complete signed release inventory and reports its publishing usage."
    dependsOn("stageMavenCentralRelease")
    commandLine("node", "scripts/maven-publication-report.mjs",
        centralRepository.get().asFile.path,
        project.group.toString(), project.version.toString(), releasePublishModules.joinToString(","),
        "signed", layout.buildDirectory.file("maven-central/report.json").get().asFile.path)
}

tasks.register<Zip>("bundleMavenCentralRelease") {
    group = "publishing"
    description = "Creates an inspectable signed release bundle without contacting Maven Central."
    dependsOn("verifyMavenCentralBundle")
    from(centralRepository)
    destinationDirectory.set(layout.buildDirectory.dir("maven-central"))
    archiveFileName.set("MuYunDatabase-${project.version}.zip")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

extensions.configure<io.github.jeadyx.UploaderExtension> {
    repositoryPath = centralRepository.get().asFile.path
    bundleName = "MuYunDatabase-${project.version}"
    tokenName = releaseValue("sonatype.token", "SONATYPE_TOKEN").orEmpty()
    tokenPasswd = releaseValue("sonatype.password", "SONATYPE_PASSWORD").orEmpty()
}

tasks.named("2.uploadDeploymentDir") {
    dependsOn("verifyMavenCentralReleaseMode", "verifyReleaseCredentials", "verifyReleaseTagVersion")
    dependsOn("verifyMavenCentralBundle")
}

tasks.register<Exec>("publishReleaseToSonatype") {
    group = "publishing"
    description = "Publishes all public MuYunDatabase artifacts to Maven Central through Sonatype."
    dependsOn("2.uploadDeploymentDir")
    environment("SONATYPE_TOKEN", releaseValue("sonatype.token", "SONATYPE_TOKEN").orEmpty())
    environment("SONATYPE_PASSWORD", releaseValue("sonatype.password", "SONATYPE_PASSWORD").orEmpty())
    commandLine("node", "scripts/wait-maven-central.mjs",
        layout.buildDirectory.file("sonayUploader/uploaderId").get().asFile.path, "--publish")
}

// Keep the plugin's familiar entry point, but retire its unbounded status/publish loop.
tasks.named("publishToSonatype") {
    actions.clear()
    setDependsOn(listOf("publishReleaseToSonatype"))
    description = "Compatibility alias for the bounded platform release flow."
}

subprojects {
    apply(plugin = "java")

    if (name in releasePublishModules) {
        apply {
            plugin("maven-publish")
            plugin("signing")
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withJavadocJar()
        withSourcesJar()
    }

    if (name in releasePublishModules) {
        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    pom {
                        name = "MuYun Database"
                        description =
                            "A lightweight database wrapper based on Jdbi, enabling incremental table and column creation while providing recommended CRUD functions."
                        url = "https://github.com/ximatai/MuYunDatabase"
                        licenses {
                            license {
                                name = "The Apache License, Version 2.0"
                                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                            }
                        }
                        developers {
                            developer {
                                id = "aruis"
                                name = "Rui Liu"
                                email = "lovearuis@gmail.com"
                                organization = "戏码台"
                            }
                        }
                        scm {
                            connection = "scm:git:git://github.com/ximatai/MuYunDatabase.git"
                            developerConnection = "scm:git:ssh://github.com/ximatai/MuYunDatabase.git"
                            url = "https://github.com/ximatai/MuYunDatabase"
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "sonatypeStaging"
                    url = uri(layout.buildDirectory.dir("repo"))
                }
                maven {
                    name = "consumer"
                    url = uri(rootProject.layout.buildDirectory.dir("consumer-repo"))
                }
            }
        }

        signing {
            if (mavenCentralReleaseMode.get()) {
                isRequired = true
                val keyId = releaseValue("signing.keyId", "SIGNING_KEY_ID")
                val secretKey = releaseSigningSecretKey()
                val password = releaseValue("signing.password", "SIGNING_PASSWORD")
                if (keyId != null && secretKey != null && password != null) {
                    useInMemoryPgpKeys(keyId, secretKey, password)
                }
                sign(publishing.publications["mavenJava"])
            }
        }
        tasks.withType<Sign>().configureEach {
            // A rotated public key ID must invalidate signatures even when the payload is unchanged.
            inputs.property("releaseSigningKeyId", releaseValue("signing.keyId", "SIGNING_KEY_ID").orEmpty())
            dependsOn(rootProject.tasks.named("verifyMavenCentralReleaseMode"),
                rootProject.tasks.named("verifyReleaseTagVersion"),
                rootProject.tasks.named("verifyReleaseSigningCredentials"))
        }
        tasks.matching {
            it.name == "publishMavenJavaPublicationToSonatypeStagingRepository" ||
                it.name == "publishAllPublicationsToSonatypeStagingRepository"
        }.configureEach {
            dependsOn(rootProject.tasks.named("verifyMavenCentralReleaseMode"),
                rootProject.tasks.named("verifyReleaseTagVersion"),
                rootProject.tasks.named("verifyReleaseSigningCredentials"))
        }
    }

    tasks.withType<Javadoc>().configureEach {
        val options = options as StandardJavadocDocletOptions
        options.addStringOption("Xdoclint:none", "-quiet")
        options.addBooleanOption("Xwerror", false)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform {
            providers.gradleProperty("muyun.test.includeTags")
                .orNull
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?.let { includeTags(*it.toTypedArray()) }
            providers.gradleProperty("muyun.test.excludeTags")
                .orNull
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?.let { excludeTags(*it.toTypedArray()) }
        }
    }

}
