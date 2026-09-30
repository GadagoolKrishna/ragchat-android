plugins {
    id("ragchat.android-library")
}

android {
    namespace = "com.ragchat.sdk"
}

dependencies {
    api(project(":sdk-api"))
    api(project(":sdk-core"))
    api(project(":sdk-ingestion"))
    api(project(":sdk-retrieval"))
    api(project(":sdk-governance"))
    api(project(":sdk-android-storage"))
    api(project(":sdk-android-parsers"))
    api(project(":sdk-android-embeddings"))
    api(project(":sdk-android-llm-local"))
    api(project(":sdk-android-llm-cloud"))
    api(project(":sdk-android-models"))
    api(project(":sdk-android-work"))
    api(project(":sdk-ui-compose"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(project(":sdk-testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
