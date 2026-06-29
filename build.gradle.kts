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

fun Project.releaseCredentialValues(): Map<String, String?> {
    return mapOf(
        "sonatype.token or SONATYPE_TOKEN" to releaseValue("sonatype.token", "SONATYPE_TOKEN"),
        "sonatype.password or SONATYPE_PASSWORD" to releaseValue("sonatype.password", "SONATYPE_PASSWORD"),
        "signing.keyId or SIGNING_KEY_ID" to releaseValue("signing.keyId", "SIGNING_KEY_ID"),
        "signing.secretKey/signing.secretKeyBase64 or SIGNING_SECRET_KEY/SIGNING_SECRET_KEY_BASE64" to
                releaseSigningSecretKey(),
        "signing.password or SIGNING_PASSWORD" to releaseValue("signing.password", "SIGNING_PASSWORD")
    )
}

fun Project.requireReleaseCredentials() {
    val missing = releaseCredentialValues().filterValues { it.isNullOrBlank() }.keys
    require(missing.isEmpty()) {
        "Missing required Gradle properties for Sonatype publish: ${missing.joinToString(", ")}"
    }
}

allprojects {
    group = "net.ximatai.muyun.database"
//    version = "1.0.0-SNAPSHOT"
    version = "3.26.14"

    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/repository/maven") }
        mavenCentral()
    }
}

tasks.register("publishReleaseToSonatype") {
    group = "publishing"
    description = "Publish release modules (core/jdbi/starter/quarkus) to Sonatype."

    dependsOn("verifyReleaseCredentials")
    dependsOn(releasePublishModules.map { ":$it:clean" })
    dependsOn("cleanLocalDeploymentDir")
    dependsOn(releasePublishModules.map { ":$it:publishToSonatype" })

    doFirst {
        requireReleaseCredentials()
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

tasks.register("publishReleaseToLocalRepository") {
    group = "publishing"
    description = "Publish release modules (core/jdbi/starter/quarkus) to the local build repositories."

    dependsOn(releasePublishModules.map { ":$it:publishAllPublicationsToMavenRepository" })
}

subprojects {
    apply {
        plugin("java")
        plugin("maven-publish")
        plugin("signing")
        plugin("io.github.jeadyx.sonatype-uploader")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withJavadocJar()
        withSourcesJar()
    }

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
                url = uri(layout.buildDirectory.dir("repo"))
            }
        }
    }

    sonatypeUploader {
        repositoryPath = layout.buildDirectory.dir("repo").get().asFile.path
        tokenName = releaseValue("sonatype.token", "SONATYPE_TOKEN").orEmpty()
        tokenPasswd = releaseValue("sonatype.password", "SONATYPE_PASSWORD").orEmpty()
    }

    signing {
        sign(publishing.publications["mavenJava"])
        useInMemoryPgpKeys(
            releaseValue("signing.keyId", "SIGNING_KEY_ID").orEmpty(),
            releaseSigningSecretKey().orEmpty(),
            releaseValue("signing.password", "SIGNING_PASSWORD").orEmpty()
        )
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

    tasks.matching { it.name == "publishToSonatype" }.configureEach {
        dependsOn("publishAllPublicationsToMavenRepository")
    }
    tasks.matching { it.name == "1.createDeploymentDir" || it.name == "2.uploadDeploymentDir" }.configureEach {
        dependsOn("publishAllPublicationsToMavenRepository")
    }
}
