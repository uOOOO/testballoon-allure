package io.github.uoooo.testballoon.allure.android

import de.infix.testBalloon.framework.core.TestCompartment
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.framework.core.executionReport
import io.github.uoooo.testballoon.allure.AllureExecutionReport
import io.github.uoooo.testballoon.allure.AllureResultsSink

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
    sink: AllureResultsSink = TestStorageResultsSink()
) : TestSession(
    testConfig = testConfig.executionReport(AllureExecutionReport(sink)),
    defaultCompartment = defaultCompartment
)
