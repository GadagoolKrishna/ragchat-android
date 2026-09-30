plugins {
    id("ragchat.android-library")
}

android {
    namespace = "com.ragchat.llm.cloud"
}

dependencies {
    api(project(":sdk-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    testImplementation(project(":sdk-testing"))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.junit)
    testImplementation(libs.json.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
