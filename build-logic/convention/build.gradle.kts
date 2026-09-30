plugins {
    `kotlin-dsl`
}

group = "com.ragchat.buildlogic"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.dokka.gradlePlugin)
    implementation(libs.bcv.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    implementation(libs.cyclonedx.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("pureKotlinModule") {
            id = "ragchat.pure-kotlin-module"
            implementationClass = "com.ragchat.buildlogic.PureKotlinModulePlugin"
        }
        register("androidLibrary") {
            id = "ragchat.android-library"
            implementationClass = "com.ragchat.buildlogic.AndroidLibraryPlugin"
        }
        register("quality") {
            id = "ragchat.quality"
            implementationClass = "com.ragchat.buildlogic.QualityPlugin"
        }
        register("publishing") {
            id = "ragchat.publishing"
            implementationClass = "com.ragchat.buildlogic.PublishingPlugin"
        }
        register("architectureCheck") {
            id = "ragchat.architecture-check"
            implementationClass = "com.ragchat.buildlogic.ArchitectureCheckPlugin"
        }
        register("archUnitTest") {
            id = "ragchat.arch-unit-test"
            implementationClass = "com.ragchat.buildlogic.ArchUnitTestPlugin"
        }
    }
}
