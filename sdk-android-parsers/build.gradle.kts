plugins {
    id("ragchat.android-library")
}

android {
    namespace = "com.ragchat.parsers"
}

dependencies {
    api(project(":sdk-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
