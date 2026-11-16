pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
  }
}

rootProject.name = "KeySnap"

include(":app")
include(":bridge")

val edition = providers.gradleProperty("keysnap.edition").orNull
  ?: file("local.properties").takeIf { it.isFile }?.let { local ->
    java.util.Properties().apply { local.inputStream().use(::load) }.getProperty("keysnap.edition")
  } ?: "oss"
if (edition == "pro") {
  listOf("ai_model", "ai_smart_1", "ai_smart_2").forEach { pack ->
    require(file("pro/$pack/build.gradle.kts").isFile) {
      "keysnap.edition=pro requires pro/$pack/build.gradle.kts"
    }
    include(":$pack")
    project(":$pack").projectDir = file("pro/$pack")
  }
}
