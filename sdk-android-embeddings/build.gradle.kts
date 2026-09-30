plugins {
    id("ragchat.android-library")
}

android {
    namespace = "com.ragchat.embeddings"
}

dependencies {
    api(project(":sdk-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.litert.support)

    testImplementation(project(":sdk-testing"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
}

