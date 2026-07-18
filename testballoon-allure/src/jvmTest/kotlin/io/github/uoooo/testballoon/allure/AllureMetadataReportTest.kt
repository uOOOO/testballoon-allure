package io.github.uoooo.testballoon.allure

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.executionReport
import de.infix.testBalloon.framework.core.internal.FrameworkTestUtilities
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.internal.TestBalloonInternalTestingApi
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `explicit suite labels replace the path-derived suites trio`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-suites").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "suitesSuite",
            testConfig =
            TestConfig
                .allure {
                    parentSuite("Web")
                    suite("Shop")
                    subSuite("Checkout")
                }.executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            testSuite("inner") {
                test("listed") {
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
            assertTrue(json.contains("\"name\":\"parentSuite\",\"value\":\"Web\""), "explicit parentSuite")
            assertTrue(json.contains("\"name\":\"suite\",\"value\":\"Shop\""), "explicit suite")
            assertTrue(json.contains("\"name\":\"subSuite\",\"value\":\"Checkout\""), "explicit subSuite")
            assertEquals(1, occurrences(json, "\"name\":\"suite\""), "derived suite label suppressed")
            assertEquals(1, occurrences(json, "\"name\":\"parentSuite\""), "derived parentSuite label suppressed")
        }
        resultsDir.deleteRecursively()
    }

    @Test
    fun `a partial explicit suite declaration disables the whole derived trio`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-suites-partial").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "partialSuitesSuite",
                testConfig =
                TestConfig
                    .allure { suite("Shop") }
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                testSuite("inner") {
                    testSuite("deeper") {
                        test("classified") {
                            // passing test body
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
                assertTrue(json.contains("\"name\":\"suite\",\"value\":\"Shop\""), "explicit suite")
                assertEquals(1, occurrences(json, "\"name\":\"suite\""), "single suite label only")
                assertFalse(json.contains("\"name\":\"parentSuite\""), "derived parentSuite not emitted")
                assertFalse(json.contains("\"name\":\"subSuite\""), "derived subSuite not emitted")
            }
            resultsDir.deleteRecursively()
        }

    @Test
    fun `suite labels merge across hierarchy levels like other scalars`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-suites-merge").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "suitesMergeSuite",
            testConfig =
            TestConfig
                .allure { suite("Shop") }
                .executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            testSuite(
                "inner",
                testConfig = TestConfig.allure { subSuite("Checkout") }
            ) {
                test("merged") {
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
            assertTrue(json.contains("\"name\":\"suite\",\"value\":\"Shop\""), "inherited suite kept")
            assertTrue(json.contains("\"name\":\"subSuite\",\"value\":\"Checkout\""), "child subSuite added")
            assertEquals(1, occurrences(json, "\"name\":\"suite\""), "single suite label only")
        }
        resultsDir.deleteRecursively()
    }

    @Test
    fun `displayName overrides the list entry but not the structural fields`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-displayname").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "displayNameSuite",
                testConfig = TestConfig.executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                test(
                    "pays with a saved card",
                    testConfig = TestConfig.allure { displayName("Checkout — pays with a saved card") }
                ) {
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
                    json.contains("\"name\":\"Checkout — pays with a saved card\""),
                    "display name overrides the result name"
                )
                assertTrue(
                    json.contains("\"fullName\":\"displayNameSuite > pays with a saved card\""),
                    "fullName keeps the declared path leaf"
                )
                assertTrue(
                    json.contains("\"name\":\"testMethod\",\"value\":\"pays with a saved card\""),
                    "testMethod label keeps the declared name"
                )
            }
            resultsDir.deleteRecursively()
        }

    @Test
    fun `a suite-level displayName fails fast with guidance`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-displayname-suite").toFile()

        // The framework wraps configuration failures (TestConfigurationError), so assert on the chain.
        val failure = assertFailsWith<Throwable> {
            val suite by testSuite(
                qualifiedPropertyName = "suiteDisplayNameSuite",
                testConfig =
                TestConfig
                    .allure { displayName("must-not-be-on-a-suite") }
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                test("never runs") {
                    // never reached — the suite configuration is rejected
                }
            }

            FrameworkTestUtilities.withTestReport(suite) {}
        }
        val chainMessages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.joinToString("\n")
        assertTrue(chainMessages.contains("suite()"), "message guides to the suite label functions")
        resultsDir.deleteRecursively()
    }

    private fun occurrences(text: String, part: String): Int = text.split(part).size - 1

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
