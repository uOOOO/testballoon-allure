package io.github.uoooo.testballoon.allure

import de.infix.testBalloon.framework.core.TestCompartment
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.framework.core.executionReport

/**
 * A [TestSession] with the [AllureExecutionReport] pre-wired module-wide. A consuming module
 * activates the whole Allure integration with a single declaration:
 *
 * ```
 * class MyTestSession : AllureTestSession()
 * ```
 *
 * The results directory comes from the `allure.results.directory` system property (which the Allure
 * Gradle plugin sets on test tasks), falling back to `build/allure-results`.
 *
 * Additional module-wide configuration chains onto [TestSession.DefaultConfiguration]:
 *
 * ```
 * class MyTestSession : AllureTestSession(
 *     testConfig = DefaultConfiguration.allure { epic("Shop") }
 * )
 * ```
 */
public open class AllureTestSession protected constructor(
    testConfig: TestConfig = DefaultConfiguration,
    defaultCompartment: () -> TestCompartment = { TestCompartment.Default },
    resultsDirectory: String = System.getProperty("allure.results.directory") ?: "build/allure-results"
) : TestSession(
    testConfig = testConfig
        .withAllurePortablePackages()
        .executionReport(AllureExecutionReport(resultsDirectory)),
    defaultCompartment = defaultCompartment
)

/**
 * If TestBalloon's Robolectric integration is on the classpath, registers this library's package as
 * portable (host-loaded), so runtime data recorded inside the Robolectric sandbox reaches the report.
 * Session-wide equivalent of `robolectric { portablePackages += "io.github.uoooo.testballoon.allure" }`
 * (settings are inherited by every suite). No-op without Robolectric on the classpath.
 *
 * Reflection is used because this pure-JVM module must not depend on the optional, Android-only
 * Robolectric integration.
 */
private fun TestConfig.withAllurePortablePackages(): TestConfig {
    val robolectricConfigKt = try {
        Class.forName("de.infix.testBalloon.integration.robolectric.TestConfigKt")
    } catch (_: ClassNotFoundException) {
        return this // no Robolectric in this test module
    }

    val configure: (Any) -> Unit = { settings ->
        @Suppress("UNCHECKED_CAST")
        val portablePackages =
            settings.javaClass.getMethod("getPortablePackages").invoke(settings) as MutableSet<String>
        portablePackages += AllureTestSession::class.java.packageName
    }

    // Invokes the extension `TestConfig.robolectric(configure)` = static TestConfigKt.robolectric(...)
    return robolectricConfigKt
        .getMethod("robolectric", TestConfig::class.java, Function1::class.java)
        .invoke(null, this, configure) as TestConfig
}
