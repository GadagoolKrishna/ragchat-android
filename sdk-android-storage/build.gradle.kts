plugins {
    id("ragchat.android-library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ragchat.storage"

    defaultConfig {
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    api(project(":sdk-api"))
    api(project(":sdk-ingestion"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.sqlite.ktx)

    // SQLCipher
    implementation(libs.sqlcipher.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
