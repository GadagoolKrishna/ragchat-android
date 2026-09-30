package com.ragchat.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class ArchUnitTestPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            val catalogs = extensions.getByType(VersionCatalogsExtension::class.java)
            val libs = catalogs.named("libs")

            dependencies.add("testImplementation", libs.findLibrary("archunit").get())
            dependencies.add("testImplementation", libs.findLibrary("junit").get())
        }
    }
}
