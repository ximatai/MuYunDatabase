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
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))

    implementation("org.springframework.boot:spring-boot-starter:4.1.0")
    implementation("org.springframework.boot:spring-boot-starter-jdbc:4.1.0")
    implementation("org.postgresql:postgresql:42.7.11")

    implementation("net.ximatai.muyun.database:muyun-database-spring-boot-starter:3.26.14")
}

tasks.test {
    useJUnitPlatform()
}
