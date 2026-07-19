import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.testballoon) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

// Shared publishing configuration for every module that applies the vanniktech plugin.
// Coordinates come from the project: group/version (gradle.properties) + module name.
subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            signAllPublications()

            pom {
                name.set(project.name)
                // provider: the module's description is assigned after its plugins are applied.
                description.set(project.provider { project.description })
                url.set("https://github.com/uOOOO/testballoon-allure")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("uOOOO")
                        name.set("Seunghun Choe")
                        url.set("https://github.com/uOOOO")
                    }
                }
                scm {
                    url.set("https://github.com/uOOOO/testballoon-allure")
                    connection.set("scm:git:git://github.com/uOOOO/testballoon-allure.git")
                    developerConnection.set("scm:git:ssh://git@github.com/uOOOO/testballoon-allure.git")
                }
            }
        }
    }
}
