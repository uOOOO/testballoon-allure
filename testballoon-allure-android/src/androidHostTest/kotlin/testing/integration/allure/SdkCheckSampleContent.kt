// NOTE: This package name intentionally deviates from the usual scheme. Classes in
// "de.infix.testBalloon.*" (framework default) and "io.github.uoooo.testballoon.allure.*"
// (via portablePackages in the suite configs) are excluded from Robolectric instrumentation —
// they run in the host classloader where Build.VERSION.SDK_INT == 0, which would fail
// (or silently skew) the assertion below. Keep this content outside those packages.
package testing.integration.allure

import android.os.Build
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import io.github.uoooo.testballoon.allure.AllureSeverity
import io.github.uoooo.testballoon.allure.allure
import io.github.uoooo.testballoon.allure.allureAttachment
import io.github.uoooo.testballoon.allure.allureParameter
import io.github.uoooo.testballoon.allure.allureStep
import kotlin.test.assertEquals

/**
 * Test content loaded and executed in the Robolectric sandbox. Touching Build.VERSION proves the
 * Android runtime is active for this test body.
 */
internal class SdkCheckSampleContent :
    RobolectricTestSuiteContent({
        test("SDK_INT matches the configured Robolectric sdk") {
            assertEquals(34, Build.VERSION.SDK_INT)
        }
    })

/**
 * Robolectric sandbox content with nested suites (robolectricTestSuite itself cannot nest, so depth
 * comes from testSuite inside the content). Build.VERSION proves the sandbox is active.
 */
internal class MultiDepthSampleContent :
    RobolectricTestSuiteContent({
        testSuite("outer group") {
            testSuite("inner group") {
                test("sdk check") {
                    assertEquals(34, Build.VERSION.SDK_INT)
                }
            }
        }
    })

/** Robolectric sandbox content that produces an attachment from inside the sandbox. */
internal class AttachmentSampleContent :
    RobolectricTestSuiteContent({
        test("attaches from sandbox") {
            allureAttachment("sandbox-log", "sdk=${Build.VERSION.SDK_INT}", "text/plain", fileExtension = "txt")
            assertEquals(34, Build.VERSION.SDK_INT)
        }
    })

/**
 * Exercises every feature of the Allure integration in one passing content, for end-to-end
 * inspection in the generated report: config-time metadata (scalar override + list append via
 * inheritance), nested groups (suite labels incl. subSuite join + registration steps), runtime
 * steps with nesting, test- and step-level attachments and parameters, and a disabled (skipped)
 * test.
 */
internal class FullFeatureSampleContent :
    RobolectricTestSuiteContent({
        testSuite(
            "full feature group",
            testConfig =
            TestConfig.allure {
                // epic is intentionally NOT set: the inherited value ("Platform") must appear in the result
                feature("Full feature coverage") // overrides the feature inherited from the top-level suite
                story("Every DSL element in one content") // appends to the inherited story list
                tag("smoke")
            }
        ) {
            testSuite("metadata") {
                test(
                    "carries the full config-time metadata set",
                    testConfig =
                    TestConfig.allure {
                        severity(AllureSeverity.CRITICAL) // overrides the inherited NORMAL
                        owner("full-feature-owner") // overrides the inherited owner
                        tag("regression", "android")
                        label("component", "allure-integration")
                        link("docs", "https://allurereport.org/docs/")
                        issue("TB-101", "https://example.org/issues/TB-101")
                        tmsLink("TC-7", "https://example.org/tms/TC-7")
                        description("Verifies that every config-time metadata field reaches the result JSON.")
                        flaky()
                    }
                ) {
                    assertEquals(34, Build.VERSION.SDK_INT)
                }

                test("is skipped by configuration", testConfig = TestConfig.disable()) {
                    error("never runs")
                }
            }

            testSuite("runtime") {
                test("records steps, attachments and parameters") {
                    allureParameter("sdk", Build.VERSION.SDK_INT.toString()) // test-level parameter
                    // test-level attachment
                    allureAttachment("environment", "sdk=${Build.VERSION.SDK_INT}", "text/plain", fileExtension = "txt")

                    allureStep("prepare input") {
                        allureParameter("input", "sample-input") // step-level parameter
                        allureStep("load fixture") {
                            // nested runtime step
                            allureAttachment(
                                "fixture",
                                "{\"value\":\"sample\"}",
                                "application/json",
                                fileExtension = "json"
                            ) // step-level
                        }
                    }
                    allureStep("verify") {
                        assertEquals(34, Build.VERSION.SDK_INT)
                    }
                }
            }
        }
    })

/** Robolectric sandbox content using a dynamic step with a step-level attachment. */
internal class StepSampleContent :
    RobolectricTestSuiteContent({
        test("steps inside sandbox") {
            allureStep("check sdk") {
                allureAttachment("sdk-log", "sdk=${Build.VERSION.SDK_INT}", "text/plain", fileExtension = "txt")
                assertEquals(34, Build.VERSION.SDK_INT)
            }
        }
    })
