plugins {
    id("ragchat.pure-kotlin-module")
}

dependencies {
    api(project(":sdk-api"))
    api(libs.kotlinx.coroutines.core)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)

    testImplementation(libs.kotlin.test)
}
