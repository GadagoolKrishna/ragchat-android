plugins {
    id("ragchat.android-library")
}

android {
    namespace = "com.ragchat.llm.local"
}

dependencies {
    api(project(":sdk-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    // LiteRT inference
    implementation(libs.litert)
    implementation(libs.litert.gpu)

    testImplementation(project(":sdk-testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
