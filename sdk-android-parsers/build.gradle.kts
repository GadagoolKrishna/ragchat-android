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
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("xpp3:xpp3:1.1.4c")
}

