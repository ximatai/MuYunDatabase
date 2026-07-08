plugins {
    java
    id("io.quarkus") version "3.37.0"
}

group = "net.ximatai.muyun.database.samples"
version = "0.0.1-SNAPSHOT"

allprojects {
    group = "net.ximatai.muyun.database"
    version = "3.26.15"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.37.0"))
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-jdbc-h2")
    implementation(project(":muyun-database-quarkus"))
}

tasks.test {
    useJUnitPlatform()
}
