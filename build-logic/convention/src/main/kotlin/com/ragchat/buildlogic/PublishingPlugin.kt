package com.ragchat.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.plugins.signing.SigningExtension

class PublishingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("maven-publish")
            pluginManager.apply("signing")

            extensions.configure<PublishingExtension> {
                publications {
                    if (findByName("release") == null) {
                        register("release", MavenPublication::class.java) {
                            groupId = "com.ragchat.sdk"
                            artifactId = project.name
                            version = project.version.toString()

                            if (plugins.hasPlugin("com.android.library")) {
                                afterEvaluate {
                                    components.findByName("release")?.let {
                                        from(it)
                                    }
                                }
                            } else if (plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
                                from(components["java"])
                            }

                            pom {
                                name.set(project.name)
                                description.set("RagChat Android SDK module: ${project.name}")
                                url.set("https://github.com/ragchat/ragchat-android")
                                licenses {
                                    license {
                                        name.set("The Apache License, Version 2.0")
                                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                                    }
                                }
                                developers {
                                    developer {
                                        id.set("ragchat-team")
                                        name.set("RagChat Team")
                                    }
                                }
                                scm {
                                    connection.set("scm:git:git://github.com/ragchat/ragchat-android.git")
                                    developerConnection.set("scm:git:ssh://github.com/ragchat/ragchat-android.git")
                                    url.set("https://github.com/ragchat/ragchat-android")
                                }
                            }
                        }
                    }
                }

                repositories {
                    maven {
                        name = "BuildDirectory"
                        url = uri(layout.buildDirectory.dir("repo"))
                    }
                }
            }

            extensions.configure<SigningExtension> {
                val signingKey = findProperty("signingKey") as? String
                val signingPassword = findProperty("signingPassword") as? String
                if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
                    useInMemoryPgpKeys(signingKey, signingPassword)
                    val publishing = extensions.getByType(PublishingExtension::class.java)
                    sign(publishing.publications)
                }
            }
        }
    }
}
