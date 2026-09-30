package com.ragchat.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CheckNoAndroidImportsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @TaskAction
    fun checkNoAndroidImports() {
        val violations = mutableListOf<String>()
        val androidImportRegex = Regex("""^\s*import\s+android\..*""")

        sourceFiles.files.forEach { file ->
            if (file.isFile && (file.extension == "kt" || file.extension == "java")) {
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (androidImportRegex.matches(line)) {
                            violations.add("${file.absolutePath}:${index + 1}: Forbidden android.* import found: '$line'")
                        }
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Architecture Rule Violation in pure module '${project.path}':")
                appendLine("Pure Kotlin/JVM modules must have ZERO android.* imports!")
                violations.forEach { appendLine("  - $it") }
            }
            throw GradleException(message)
        }
    }
}

class ArchitectureCheckPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val checkTask = project.tasks.register("checkNoAndroidImports", CheckNoAndroidImportsTask::class.java) {
            description = "Verifies that pure Kotlin/JVM modules do not import any android.* packages."
            group = "verification"
            val srcDir = project.file("src")
            if (srcDir.exists()) {
                val files = project.fileTree(srcDir).matching {
                    include("**/*.kt", "**/*.java")
                }
                sourceFiles.from(files)
            }
        }

        project.tasks.matching { it.name == "check" }.configureEach {
            dependsOn(checkTask)
        }
    }
}
