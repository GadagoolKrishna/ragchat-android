plugins {
    id("ragchat.pure-kotlin-module")
}

dependencies {
    api(project(":sdk-api"))
    implementation(project(":sdk-core"))
    implementation(project(":sdk-retrieval"))
    implementation(project(":sdk-governance"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":sdk-testing"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.archunit)
    testImplementation(libs.junit)
}
