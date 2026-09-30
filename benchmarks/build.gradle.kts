plugins {
    alias(libs.plugins.android.test)
    id("ragchat.quality")
}

android {
    namespace = "com.ragchat.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    targetProjectPath = ":sample-app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.benchmark.junit4)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(project(":sdk-api"))
    implementation(project(":sdk-android-storage"))
    implementation(project(":sdk-retrieval"))
    implementation(libs.junit)
    implementation(libs.kotlin.test)
}
