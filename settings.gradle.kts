pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ragchat-android"

// Pure Kotlin/JVM modules
include(":sdk-api")
include(":sdk-testing")
include(":sdk-core")
include(":sdk-ingestion")
include(":sdk-retrieval")
include(":sdk-governance")
include(":eval")

// Android modules
include(":sdk-android-storage")
include(":sdk-android-parsers")
include(":sdk-android-embeddings")
include(":sdk-android-llm-local")
include(":sdk-android-llm-cloud")
include(":sdk-android-models")
include(":sdk-android-work")
include(":sdk-ui-compose")

// Aggregator
include(":sdk")

// Sample and Benchmarks
include(":sample-app")
include(":benchmarks")
