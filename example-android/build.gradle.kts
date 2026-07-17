plugins {
    // Kotlin support is built into AGP since 9.0 — no org.jetbrains.kotlin.android needed.
    alias(libs.plugins.android.application)
    alias(libs.plugins.testballoon)
    alias(libs.plugins.kotlinter)
}

description = "Example app running TestBalloon instrumented tests with Allure reporting"

android {
    namespace = "io.github.uoooo.testballoon.allure.example"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.uoooo.testballoon.allure.example"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Lets TestStorage store files (the Allure results) so that connectedAndroidTest
        // retrieves them into build/outputs/connected_android_test_additional_output/.
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }
}

dependencies {
    androidTestImplementation(project(":testballoon-allure-android"))
    androidTestImplementation(libs.androidx.test.runner)
    // Installs the test services APK providing TestStorage (see useTestStorageService above):
    androidTestUtil(libs.androidx.test.services)
}
