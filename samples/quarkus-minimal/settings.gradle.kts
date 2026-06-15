pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "quarkus-minimal"

include(":muyun-database-core")
include(":muyun-database-jdbi")
include(":muyun-database-quarkus")
include(":muyun-database-quarkus-deployment")

project(":muyun-database-core").projectDir = file("../../muyun-database-core")
project(":muyun-database-jdbi").projectDir = file("../../muyun-database-jdbi")
project(":muyun-database-quarkus").projectDir = file("../../muyun-database-quarkus")
project(":muyun-database-quarkus-deployment").projectDir = file("../../muyun-database-quarkus-deployment")
