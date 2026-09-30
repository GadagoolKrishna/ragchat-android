package com.ragchat.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class QualityPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            pluginManager.apply("com.diffplug.spotless")

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                allRules = false
                val configFile = rootProject.file("config/detekt/detekt.yml")
                if (configFile.exists()) {
                    config.setFrom(configFile)
                }
            }

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("src/**/*.kt")
                    ktlint()
                    trimTrailingWhitespace()
                    endWithNewline()
                }
            }
        }
    }
}
