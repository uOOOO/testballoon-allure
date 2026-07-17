package io.github.uoooo.testballoon.allure

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.executionReport
import de.infix.testBalloon.framework.core.internal.FrameworkTestUtilities
import de.infix.testBalloon.framework.core.testScope
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.internal.TestBalloonInternalTestingApi
import kotlinx.coroutines.delay
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(TestBalloonInternalTestingApi::class)
class AllureRuntimeStepsTest {
    @BeforeTest
    fun initialize() {
        FrameworkTestUtilities.resetTestFramework()
    }

    @Test
    fun `allureStep records nested steps with own timing under the leaf step`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-steps-rt").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "runtimeStepsSuite",
                // Disable the framework's default TestScope: its virtual-time dispatcher makes delay()
                // return instantly, which would defeat this test's real-timing assertion below.
                testConfig =
                TestConfig
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
                    .testScope(false)
            ) {
                test("does work") {
                    allureStep("outer step") {
                        allureStep("inner step") {
                            delay(50)
                        }
                    }
                }
            }

            FrameworkTestUtilities.withTestReport(suite) {
                val json =
                    resultsDir
                        .listFiles { f -> f.name.endsWith("-result.json") }
                        .orEmpty()
                        .first()
                        .readText()
                // runtime steps graft under the registration-time leaf step ("does work")
                assertTrue(
                    json.contains("\"name\":\"does work\",\"status\":\"passed\",\"steps\":[{\"name\":\"outer step\""),
                    "runtime steps nest under the registration-time leaf step"
                )
                assertTrue(
                    json.contains("\"name\":\"outer step\",\"status\":\"passed\",\"steps\":[{\"name\":\"inner step\""),
                    "runtime steps nest inside each other"
                )
                // the inner step carries its own timing, spanning at least the delay. The inner step's
                // own "start" is the first one following its name (it has no nested steps of its own).
                val timing =
                    Regex("\"name\":\"inner step\".*?\"start\":(\\d+),\"stop\":(\\d+)")
                        .find(json)
                assertNotNull(timing, "inner step has start/stop")
                val (start, stop) = timing.destructured
                assertTrue(stop.toLong() >= start.toLong() + 30, "inner step timing spans the delay")
            }
            resultsDir.deleteRecursively()
        }

    @Test
    fun `assertion failure in a step marks it and enclosing steps failed`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-steps-fail").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "runtimeStepsFailSuite",
            testConfig = TestConfig.executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            test("fails inside step") {
                allureStep("outer") {
                    allureStep("inner") {
                        throw AssertionError("boom")
                    }
                }
            }
        }

        FrameworkTestUtilities.withTestReport(suite) {
            val json =
                resultsDir
                    .listFiles { f -> f.name.endsWith("-result.json") }
                    .orEmpty()
                    .first()
                    .readText()
            assertTrue(json.contains("\"name\":\"inner\",\"status\":\"failed\""), "failing step marked failed")
            assertTrue(json.contains("\"name\":\"outer\",\"status\":\"failed\""), "enclosing step marked failed")
            assertTrue(json.contains("\"status\":\"failed\",\"statusDetails\""), "test itself failed")
        }
        resultsDir.deleteRecursively()
    }

    @Test
    fun `non-assertion exception in a step marks it broken`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-steps-broken").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "runtimeStepsBrokenSuite",
            testConfig = TestConfig.executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            test("breaks inside step") {
                allureStep("fragile") {
                    throw IllegalStateException("infra down")
                }
            }
        }

        FrameworkTestUtilities.withTestReport(suite) {
            val json =
                resultsDir
                    .listFiles { f -> f.name.endsWith("-result.json") }
                    .orEmpty()
                    .first()
                    .readText()
            assertTrue(json.contains("\"name\":\"fragile\",\"status\":\"broken\""), "step marked broken")
        }
        resultsDir.deleteRecursively()
    }

    @Test
    fun `attachments and parameters land on the current step or the test`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-steps-data").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "runtimeStepDataSuite",
            testConfig = TestConfig.executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            test("attaches at levels") {
                allureParameter("env", "staging")
                allureAttachment("test-level", "root content", "text/plain")
                allureStep("step with data") {
                    allureParameter("recipient", "a@b.c")
                    allureAttachment("step-level", "inner content", "text/plain")
                }
            }
        }

        FrameworkTestUtilities.withTestReport(suite) {
            val json =
                resultsDir
                    .listFiles { f -> f.name.endsWith("-result.json") }
                    .orEmpty()
                    .first()
                    .readText()
            assertTrue(json.contains("\"name\":\"env\",\"value\":\"staging\""), "test-level parameter")
            assertTrue(json.contains("\"name\":\"recipient\",\"value\":\"a@b.c\""), "step-level parameter")
            assertTrue(json.contains("\"name\":\"test-level\""), "test-level attachment entry")
            // the step-level attachment entry appears inside the step object, before its start/stop
            assertTrue(
                json.contains("\"steps\":[],\"attachments\":[{\"name\":\"step-level\""),
                "step-level attachment nested in the step itself, not the test"
            )
            val attachmentFiles = resultsDir.listFiles { f -> f.name.endsWith("-attachment") }.orEmpty()
            assertEquals(2, attachmentFiles.size, "both attachment files written")
        }
        resultsDir.deleteRecursively()
    }

    @Test
    fun `allureStep propagates the body's return value`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-steps-plain").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "runtimePlainSuite",
            testConfig = TestConfig.executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            test("plain test with step") {
                val value = allureStep("compute") { 42 }
                assertEquals(42, value)
            }
        }

        FrameworkTestUtilities.withTestReport(suite) {
            val json =
                resultsDir
                    .listFiles { f -> f.name.endsWith("-result.json") }
                    .orEmpty()
                    .first()
                    .readText()
            // runtime steps graft under the test's own registration-time leaf step
            assertTrue(json.contains("\"name\":\"compute\",\"status\":\"passed\""), "step recorded")
        }
        resultsDir.deleteRecursively()
    }
}
