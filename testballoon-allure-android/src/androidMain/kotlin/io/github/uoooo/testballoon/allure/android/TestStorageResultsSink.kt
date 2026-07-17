package io.github.uoooo.testballoon.allure.android

import androidx.test.services.storage.TestStorage
import io.github.uoooo.testballoon.allure.AllureResultsSink

/**
 * An [AllureResultsSink] writing each file via [TestStorage] into `<resultsDirectoryPath>/<fileName>`.
 *
 * TestStorage stores files in a device location managed by the androidx test services (surviving the
 * test APK's uninstallation), from which Gradle's `connectedAndroidTest` retrieves them into
 * `build/outputs/`. It requires the test services APK on the device and the instrumentation argument
 * `useTestStorageService=true`:
 *
 * ```
 * android.defaultConfig.testInstrumentationRunnerArguments["useTestStorageService"] = "true"
 * dependencies.androidTestUtil("androidx.test.services:test-services:<version>")
 * ```
 *
 * This sink only works under a running instrumentation on a device or emulator. Elsewhere (JVM or
 * Robolectric host tests), construction or the first write fails — use
 * [AllureTestSession][io.github.uoooo.testballoon.allure.AllureTestSession] or a
 * [FileSystemResultsSink][io.github.uoooo.testballoon.allure.FileSystemResultsSink] there.
 */
public class TestStorageResultsSink(private val resultsDirectoryPath: String = "allure-results") : AllureResultsSink {
    private val testStorage = TestStorage()

    override fun write(fileName: String, content: ByteArray) {
        testStorage.openOutputFile("$resultsDirectoryPath/$fileName").use { it.write(content) }
    }
}
