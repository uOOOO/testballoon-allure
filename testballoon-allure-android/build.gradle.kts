plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.testballoon)
    alias(libs.plugins.kotlinter)
}

description = "Allure reporting for the TestBalloon test framework in instrumented (device) tests"

kotlin {
    explicitApi()
    jvmToolchain(17) // Robolectric requires a JDK 17+ test runtime.

    android {
        namespace = "io.github.uoooo.testballoon.allure.android"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder {} // Enables the androidHostTest source set (Robolectric samples)
    }

    sourceSets {
        androidMain {
            dependencies {
                api(project(":testballoon-allure"))
                implementation(libs.androidx.test.storage)
            }
        }

        named("androidHostTest") {
            dependencies {
                implementation(libs.testballoon.integration.robolectric)
                implementation(libs.kotlin.test)
                implementation(libs.junit4)
                implementation(libs.androidx.test.core)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    val allureResults = layout.buildDirectory.dir("allure-results")
    // Declared as an output so cached/up-to-date runs still provide the results (e.g. for CI upload).
    outputs.dir(allureResults)
    systemProperty("allure.results.directory", allureResults.get().asFile.absolutePath)
}
