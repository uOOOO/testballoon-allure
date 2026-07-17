package io.github.uoooo.testballoon.allure

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.executionReport
import de.infix.testBalloon.framework.core.internal.FrameworkTestUtilities
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.internal.TestBalloonInternalTestingApi
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(TestBalloonInternalTestingApi::class)
class AllureMetadataReportTest {
    @BeforeTest
    fun initialize() {
        FrameworkTestUtilities.resetTestFramework()
    }

    @Test
    fun `builder metadata emits all labels links description and flags`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-meta").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "metaSuite",
            testConfig =
            TestConfig
                .allure {
                    epic("Shop")
                    feature("Checkout")
                    story("Checkout → Receipt")
                    description("Verifies order checkout")
                    descriptionHtml("<b>html</b>")
                    severity(AllureSeverity.CRITICAL)
                    owner("john-doe")
                    tag("smoke", "checkout")
                    label("component", "api")
                    link("docs", "https://example.org/docs")
                    issue("AUTH-123", "https://tracker.example.org/AUTH-123")
                    tmsLink("TMS-456", "https://tms.example.org/TMS-456")
                    flaky()
                    muted()
                }.executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            test("send succeeds") {
                // passing test body
            }
        }

        FrameworkTestUtilities.withTestReport(suite) {
            val json =
                resultsDir
                    .listFiles { f -> f.name.endsWith("-result.json") }
                    .orEmpty()
                    .first()
                    .readText()
            assertTrue(json.contains("\"name\":\"epic\",\"value\":\"Shop\""), "epic label")
            assertTrue(json.contains("\"name\":\"severity\",\"value\":\"critical\""), "severity label")
            assertTrue(json.contains("\"name\":\"owner\",\"value\":\"john-doe\""), "owner label")
            assertTrue(json.contains("\"name\":\"tag\",\"value\":\"smoke\""), "first tag label")
            assertTrue(json.contains("\"name\":\"tag\",\"value\":\"checkout\""), "second tag label")
            assertTrue(json.contains("\"name\":\"component\",\"value\":\"api\""), "custom label")
            assertTrue(json.contains("\"description\":\"Verifies order checkout\""), "description field")
            assertTrue(json.contains("\"descriptionHtml\":\"<b>html</b>\""), "descriptionHtml field")
            assertTrue(
                json.contains("\"name\":\"docs\",\"url\":\"https://example.org/docs\",\"type\":\"custom\""),
                "custom link"
            )
            assertTrue(
                json.contains(
                    "\"name\":\"AUTH-123\",\"url\":\"https://tracker.example.org/AUTH-123\",\"type\":\"issue\""
                ),
                "issue link"
            )
            assertTrue(
                json.contains("\"name\":\"TMS-456\",\"url\":\"https://tms.example.org/TMS-456\",\"type\":\"tms\""),
                "tms link"
            )
            assertTrue(json.contains("\"flaky\":true"), "flaky flag in statusDetails")
            assertTrue(json.contains("\"muted\":true"), "muted flag in statusDetails")
        }
        resultsDir.deleteRecursively()
    }

    @Test
    fun `child metadata merges field-wise with inherited metadata`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-merge").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "mergeSuite",
            testConfig =
            TestConfig
                .allure {
                    epic("Shop")
                    feature("Checkout")
                    severity(AllureSeverity.NORMAL)
                    tag("smoke")
                }.executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            testSuite(
                "inner",
                testConfig =
                TestConfig.allure {
                    severity(AllureSeverity.CRITICAL)
                    tag("checkout")
                }
            ) {
                test("child test") {
                    // passing test body
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
            assertTrue(json.contains("\"name\":\"epic\",\"value\":\"Shop\""), "inherited epic kept")
            assertTrue(json.contains("\"name\":\"severity\",\"value\":\"critical\""), "child severity overrides")
            assertFalse(json.contains("\"value\":\"normal\""), "inherited severity replaced, not duplicated")
            assertTrue(json.contains("\"name\":\"tag\",\"value\":\"smoke\""), "inherited tag kept")
            assertTrue(json.contains("\"name\":\"tag\",\"value\":\"checkout\""), "child tag appended")
        }
        resultsDir.deleteRecursively()
    }

    @Test
    fun `issue and tms links resolve urls from system property patterns`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-linkpattern").toFile()
        System.setProperty("allure.link.issue.pattern", "https://tracker.example.org/{}")
        System.setProperty("allure.link.tms.pattern", "https://tms.example.org/{}")
        try {
            val suite by testSuite(
                qualifiedPropertyName = "linkPatternSuite",
                testConfig =
                TestConfig
                    .allure {
                        issue("AUTH-123")
                        tmsLink("TMS-456")
                    }.executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                test("linked") {
                    // passing test body
                }
            }

            FrameworkTestUtilities.withTestReport(suite) {
                val json =
                    resultsDir
                        .listFiles { f -> f.name.endsWith("-result.json") }
                        .orEmpty()
                        .first()
                        .readText()
                assertTrue(
                    json.contains("\"url\":\"https://tracker.example.org/AUTH-123\""),
                    "issue pattern applied"
                )
                assertTrue(json.contains("\"url\":\"https://tms.example.org/TMS-456\""), "tms pattern applied")
            }
        } finally {
            System.clearProperty("allure.link.issue.pattern")
            System.clearProperty("allure.link.tms.pattern")
            resultsDir.deleteRecursively()
        }
    }
}
