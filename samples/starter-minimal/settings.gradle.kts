rootProject.name = "starter-minimal"
if (!providers.gradleProperty("muyun.consumer.repository").isPresent) {
    includeBuild("../..")
}
