package io.github.uoooo.testballoon.allure

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.executionReport
import de.infix.testBalloon.framework.core.internal.FrameworkTestUtilities
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.internal.TestBalloonInternalTestingApi
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import testing.integration.allure.AttachmentSampleContent
import testing.integration.allure.MultiDepthSampleContent
import testing.integration.allure.SdkCheckSampleContent
import testing.integration.allure.StepSampleContent
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(TestBalloonInternalTestingApi::class)
class AllureRobolectricCompositionTest {
    @BeforeTest
    fun initialize() {
        FrameworkTestUtilities.resetTestFramework()
    }

    @Test
    fun `allure result json is written for a test running inside a robolectricTestSuite`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-robolectric").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "robolectricSample",
                testConfig =
                TestConfig
                    // SdkCheckSampleContent asserts SDK_INT == 34 — keep these two values in sync
                    .robolectric {
                        sdk = 34
                        // Keep this integration host-loaded in the sandbox (shared runtime buffer).
                        portablePackages += "io.github.uoooo.testballoon.allure"
                    }.allure(epic = "Platform", feature = "Sandbox", "SDK version check")
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                robolectricTestSuite<SdkCheckSampleContent>("SdkCheck")
            }

            FrameworkTestUtilities.withTestReport(suite) {
                val resultFiles = resultsDir.listFiles { f -> f.name.endsWith("-result.json") }.orEmpty()
                assertTrue(resultFiles.isNotEmpty(), "a result json must be written for the robolectric test body")

                // a robolectricTestSuite may write more than one result file; read them all
                val json = resultFiles.joinToString("\n") { it.readText() }
                assertTrue(json.contains("\"Platform\""), "epic label value present")
                assertTrue(json.contains("\"Sandbox\""), "feature label value present")
                assertTrue(json.contains("\"SDK version check\""), "story label value present")
                assertTrue(json.contains("\"passed\""), "status passed")
            }
            resultsDir.deleteRecursively()
        }

    @Test
    fun `nested suites inside a robolectricTestSuite record multi-depth steps`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-rl-depth").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "robolectricDepthSample",
                testConfig =
                TestConfig
                    .robolectric {
                        sdk = 34
                        // Keep this integration host-loaded in the sandbox (shared runtime buffer).
                        portablePackages += "io.github.uoooo.testballoon.allure"
                    }.allure(epic = "Platform", feature = "Sandbox", "SDK version check")
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                robolectricTestSuite<MultiDepthSampleContent>("SdkCheck")
            }

            FrameworkTestUtilities.withTestReport(suite) {
                val json =
                    resultsDir
                        .listFiles { f -> f.name.endsWith("-result.json") }
                        .orEmpty()
                        .first()
                        .readText()
                assertTrue(
                    json.contains(
                        "\"fullName\":\"robolectricDepthSample > SdkCheck > outer group > inner group > sdk check\""
                    ),
                    "suites nested inside robolectric content accumulate the full path in order"
                )
                assertTrue(json.contains("\"passed\""), "passed under robolectric (sandbox active)")
                resultsDir.deleteRecursively()
            }
        }

    @Test
    fun `allureAttachment works inside a robolectricTestSuite`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-rl-attach").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "robolectricAttachSample",
            testConfig =
            TestConfig
                .robolectric {
                    sdk = 34
                    // Keep this integration host-loaded in the sandbox (shared runtime buffer).
                    portablePackages += "io.github.uoooo.testballoon.allure"
                }.allure(epic = "Platform", feature = "Sandbox", "SDK version check")
                .executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            robolectricTestSuite<AttachmentSampleContent>("SdkCheck")
        }

        FrameworkTestUtilities.withTestReport(suite) {
            val json =
                resultsDir
                    .listFiles { f -> f.name.endsWith("-result.json") }
                    .orEmpty()
                    .first()
                    .readText()
            assertTrue(json.contains("\"name\":\"sandbox-log\""), "attachment entry present (from sandbox)")
            val attachmentFiles = resultsDir.listFiles { f -> f.name.endsWith("-attachment.txt") }.orEmpty()
            assertTrue(attachmentFiles.isNotEmpty(), "attachment file written from sandbox test")
            assertTrue(attachmentFiles.first().readText().contains("sdk=34"), "attachment content from sandbox")
            resultsDir.deleteRecursively()
        }
    }

    @Test
    fun `allureStep with a step attachment works inside a robolectricTestSuite`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-rl-step").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "robolectricStepSample",
                testConfig =
                TestConfig
                    .robolectric {
                        sdk = 34
                        // Keep this integration host-loaded in the sandbox (shared runtime buffer).
                        portablePackages += "io.github.uoooo.testballoon.allure"
                    }.allure(epic = "Platform", feature = "Sandbox", "SDK version check")
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                robolectricTestSuite<StepSampleContent>("SdkCheck")
            }

            FrameworkTestUtilities.withTestReport(suite) {
                val json =
                    resultsDir
                        .listFiles { f -> f.name.endsWith("-result.json") }
                        .orEmpty()
                        .first()
                        .readText()
                assertTrue(
                    json.contains("\"name\":\"check sdk\",\"status\":\"passed\""),
                    "dynamic step recorded from the sandbox"
                )
                assertTrue(json.contains("\"name\":\"sdk-log\""), "step attachment entry present")
                assertTrue(
                    json.contains("\"steps\":[],\"attachments\":[{\"name\":\"sdk-log\""),
                    "attachment recorded on the step itself, not the test"
                )
                val attachmentFiles = resultsDir.listFiles { f -> f.name.endsWith("-attachment.txt") }.orEmpty()
                assertTrue(attachmentFiles.isNotEmpty(), "attachment file written from sandbox step")
                resultsDir.deleteRecursively()
            }
        }

    /** Only referenced from [portableClasses] to force a distinct Robolectric sandbox (see below). */
    private class SandboxIsolationMarker

    @Test
    fun `a sandbox-loaded integration fails loudly instead of losing recordings`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-rl-guard").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "robolectricGuardSample",
                testConfig =
                TestConfig
                    // portablePackages intentionally NOT set and no AllureTestSession active:
                    // the sandbox acquires this integration, and the guard must turn the silent
                    // recording loss into a loud failure.
                    .robolectric {
                        sdk = 34
                        // Robolectric caches sandboxes by InstrumentationConfiguration, whose
                        // equals() ignores packagesToNotAcquire (where portablePackages ends up) —
                        // without this marker, the test could silently reuse a sandbox created by a
                        // portablePackages-configured test above (and vice versa). portableClasses
                        // feeds classesToNotAcquire, which equals() does compare, forcing a fresh,
                        // non-portable sandbox for this test only.
                        portableClasses += SandboxIsolationMarker::class
                    }
                    .allure(epic = "Platform", feature = "Sandbox", "SDK version check")
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                robolectricTestSuite<AttachmentSampleContent>("SdkCheck")
            }

            FrameworkTestUtilities.withTestReport(suite) {
                val json =
                    resultsDir
                        .listFiles { f -> f.name.endsWith("-result.json") }
                        .orEmpty()
                        .first()
                        .readText()
                assertTrue(json.contains("\"status\":\"broken\""), "guard failure recorded as broken, json=$json")
                assertTrue(json.contains("portablePackages"), "guard message guides to the fix, json=$json")
            }
            resultsDir.deleteRecursively()
        }
}
