plugins {
    id("ragchat.android-library")
}

android {
    namespace = "com.ragchat.work"
}

dependencies {
    api(project(":sdk-api"))
    api(project(":sdk-ingestion"))
    api(project(":sdk-android-storage"))
    api(project(":sdk-android-parsers"))
    api(project(":sdk-android-embeddings"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(project(":sdk-testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
