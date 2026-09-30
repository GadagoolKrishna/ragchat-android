package com.ragchat.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class PureKotlinModulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("org.jetbrains.dokka")
            pluginManager.apply("ragchat.quality")
            pluginManager.apply("ragchat.publishing")
            pluginManager.apply("ragchat.architecture-check")
            pluginManager.apply("ragchat.arch-unit-test")

            extensions.configure<JavaPluginExtension> {
                toolchain.languageVersion.set(JavaLanguageVersion.of(17))
            }

            extensions.configure<KotlinJvmProjectExtension> {
                explicitApi()
                jvmToolchain(17)
            }
        }
    }
}
