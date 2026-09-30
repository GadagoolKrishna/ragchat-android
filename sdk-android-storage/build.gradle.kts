plugins {
    id("ragchat.android-library")
}

android {
    namespace = "com.ragchat.storage"
}

dependencies {
    api(project(":sdk-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
