plugins {
    id("org.springframework.boot") version "4.1.0"
    java
}

group = "net.ximatai.muyun.database.samples"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    providers.gradleProperty("muyun.consumer.repository").orNull?.let { repository ->
        exclusiveContent {
            forRepository { maven { url = uri(repository) } }
            filter { includeGroup("net.ximatai.muyun.database") }
        }
    }
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))

    implementation("org.springframework.boot:spring-boot-starter:4.1.0")
    implementation("org.springframework.boot:spring-boot-starter-jdbc:4.1.0")
    implementation("org.postgresql:postgresql:42.7.11")

    val databaseVersion = providers.gradleProperty("muyun.consumer.version").getOrElse("3.26.25")
    implementation("net.ximatai.muyun.database:muyun-database-spring-boot-starter:$databaseVersion")
}

tasks.test {
    useJUnitPlatform()
}
