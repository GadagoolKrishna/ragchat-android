plugins {
    id("ragchat.android-library")
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.ragchat.ui.compose"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":sdk-api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
