package io.github.uoooo.testballoon.allure.android

import de.infix.testBalloon.framework.core.TestCompartment
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.framework.core.executionReport
import io.github.uoooo.testballoon.allure.AllureExecutionReport
import io.github.uoooo.testballoon.allure.AllureResultsSink
import java.io.ByteArrayOutputStream
import java.util.Properties

/**
 * A [TestSession] with the [AllureExecutionReport] pre-wired for instrumented (device) tests: results
 * are written via [TestStorageResultsSink], so `connectedAndroidTest` retrieves them into
 * `build/outputs/` (see [TestStorageResultsSink] for the required build configuration). A consuming
 * module activates the whole Allure integration with a single declaration in its device test sources:
 *
 * ```
 * class MyDeviceTestSession : AllureAndroidTestSession()
 * ```
 *
 * [environment] entries fill the Allure report's Environment widget — they are written next to the
 * results under the fixed name `environment.properties`, which the report generator expects:
 *
 * ```
 * class MyDeviceTestSession : AllureAndroidTestSession(
 *     environment = mapOf("Model" to Build.MODEL, "SDK" to Build.VERSION.SDK_INT.toString())
 * )
 * ```
 *
 * Additional module-wide configuration chains onto [TestSession.DefaultConfiguration]:
 *
 * ```
 * class MyDeviceTestSession : AllureAndroidTestSession(
 *     testConfig = DefaultConfiguration.allure { epic("Device") }
 * )
 * ```
 */
public open class AllureAndroidTestSession protected constructor(
    testConfig: TestConfig = DefaultConfiguration,
    defaultCompartment: () -> TestCompartment = { TestCompartment.Default },
    sink: AllureResultsSink = TestStorageResultsSink(),
    environment: Map<String, String> = emptyMap()
) : TestSession(
    testConfig = testConfig.executionReport(AllureExecutionReport(sink)),
    defaultCompartment = defaultCompartment
) {
    init {
        if (environment.isNotEmpty()) {
            sink.writeEnvironment(environment)
        }
    }
}

/** Writes [environment] in properties format under the fixed name the report generator expects. */
private fun AllureResultsSink.writeEnvironment(environment: Map<String, String>) {
    val content = ByteArrayOutputStream().apply {
        Properties().apply {
            environment.forEach { (key, value) -> setProperty(key, value) }
        }.store(this, null)
    }.toByteArray()
    write("environment.properties", content)
}
