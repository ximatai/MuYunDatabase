plugins {
    java
    checkstyle
    signing
    id("io.github.jeadyx.sonatype-uploader") version "2.8"
}

allprojects {
    group = "net.ximatai.muyun.database"
//    version = "1.0.0-SNAPSHOT"
    version = "2.26.8"

    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/repository/maven") }
        mavenCentral()
    }
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
        tokenName = findProperty("sonatype.token").toString()
        tokenPasswd = findProperty("sonatype.password").toString()
    }

    signing {
        sign(publishing.publications["mavenJava"])
        useInMemoryPgpKeys(
            findProperty("signing.keyId").toString(),
            findProperty("signing.secretKey").toString(),
            findProperty("signing.password").toString()
        )
    }

    tasks.withType<Javadoc>().configureEach {
        val options = options as StandardJavadocDocletOptions
        options.addStringOption("Xdoclint:none", "-quiet")
        options.addBooleanOption("Xwerror", false)
    }
}


