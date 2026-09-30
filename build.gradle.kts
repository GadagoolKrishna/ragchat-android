import org.cyclonedx.gradle.CycloneDxTask

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.kover)
    alias(libs.plugins.cyclonedx)
}

allprojects {
    group = "com.ragchat.sdk"
    version = "0.1.0-SNAPSHOT"
}

apiValidation {
    ignoredProjects += listOf("sample-app", "benchmarks")
    nonPublicMarkers += listOf(
        "com.ragchat.annotation.InternalRagChatApi"
    )
}

tasks.named<CycloneDxTask>("cyclonedxBom") {
    includeConfigs.set(listOf("runtimeClasspath", "releaseRuntimeClasspath"))
    skipConfigs.set(listOf("compileClasspath", "testCompileClasspath"))
    destination.set(layout.buildDirectory.dir("reports/sbom").get().asFile)
    outputName.set("bom")
    outputFormat.set("json")
    schemaVersion.set("1.5")
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
