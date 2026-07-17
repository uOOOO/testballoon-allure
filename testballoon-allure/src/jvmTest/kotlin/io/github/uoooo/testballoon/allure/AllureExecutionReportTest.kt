package io.github.uoooo.testballoon.allure

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.executionReport
import de.infix.testBalloon.framework.core.internal.FrameworkTestUtilities
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.framework.shared.internal.TestBalloonInternalTestingApi
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(TestBalloonInternalTestingApi::class)
class AllureExecutionReportTest {
    @BeforeTest
    fun initialize() {
        FrameworkTestUtilities.resetTestFramework()
    }

    @Test
    fun `plain suite pass writes allure result json with epic feature story labels`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "sampleSuite",
                testConfig =
                TestConfig
                    .allure(epic = "Shop", feature = "Checkout", "Checkout → Receipt")
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                test("send succeeds") {
                    // passing test body
                }
            }

            FrameworkTestUtilities.withTestReport(suite) {
                val resultFiles = resultsDir.listFiles { f -> f.name.endsWith("-result.json") }.orEmpty()
                assertTrue(resultFiles.isNotEmpty(), "a result json must be written")

                val json = resultFiles.first().readText()
                assertTrue(json.contains("\"Shop\""), "epic label value present")
                assertTrue(json.contains("\"Checkout\""), "feature label value present")
                assertTrue(json.contains("\"Checkout → Receipt\""), "story label value present")
                assertTrue(json.contains("\"passed\""), "status passed")
                assertTrue(json.contains("\"finished\""), "stage finished")
            }

            resultsDir.deleteRecursively()
        }

    @Test
    fun `nested suites and tests record a step per level using declared names`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-steps").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "stepsSuite",
                testConfig =
                TestConfig
                    .allure(epic = "Shop", feature = "Checkout", "Checkout → Receipt")
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                testSuite("when something happens") {
                    test("then it records steps") {
                        // passing
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
                // "steps":[] also appears inside each StepResult's sub-steps; check for a non-empty array instead
                assertTrue(json.contains("\"steps\":[{"), "steps must not be empty")
                assertTrue(json.contains("\"name\":\"when something happens\""), "nested suite recorded as a step")
                assertTrue(json.contains("\"name\":\"then it records steps\""), "test recorded as a step")
                assertTrue(
                    json.contains(
                        "\"name\":\"when something happens\",\"status\":\"passed\",\"steps\":[{\"name\":\"then it records steps\""
                    ),
                    "child step nests inside parent step (not flattened)"
                )
                resultsDir.deleteRecursively()
            }
        }

    @Test
    fun `failing test marks leaf step failed and ancestor step passed`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-fail").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "stepsFailSuite",
            testConfig =
            TestConfig
                .allure(epic = "Shop", feature = "Checkout", "Checkout → Receipt")
                .executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            testSuite("outer context") {
                test("inner fails") {
                    throw AssertionError("boom")
                }
            }
        }

        FrameworkTestUtilities.withTestReport(suite) {
            // Allure writer emits compact JSON; step objects serialize as {"name":...,"status":...}
            val json =
                resultsDir
                    .listFiles { f -> f.name.endsWith("-result.json") }
                    .orEmpty()
                    .first()
                    .readText()
            assertTrue(json.contains("\"name\":\"inner fails\",\"status\":\"failed\""), "leaf step failed")
            assertTrue(json.contains("\"name\":\"outer context\",\"status\":\"passed\""), "ancestor step passed")
            resultsDir.deleteRecursively()
        }
    }

    @Test
    fun `deeply nested suites record steps root-to-leaf in order`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-depth").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "depthSuite",
            testConfig =
            TestConfig
                .allure(epic = "Shop", feature = "Checkout", "Checkout → Receipt")
                .executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            testSuite("level one") {
                testSuite("level two") {
                    testSuite("level three") {
                        test("leaf test") {
                            // passing
                        }
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
            // fullName encodes the full ordered chain → pins accumulation correctness AND order across depth
            assertTrue(
                json.contains("\"fullName\":\"depthSuite > level one > level two > level three > leaf test\""),
                "names accumulate root->leaf across all depths in order"
            )
            resultsDir.deleteRecursively()
        }
    }

    @Test
    fun `path levels map to parentSuite suite and subSuite labels root-first`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-suite-labels").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "suiteLabelsSuite",
                testConfig =
                TestConfig
                    .allure(epic = "Shop", feature = "Checkout", "Checkout → Receipt")
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                testSuite("level one") {
                    testSuite("level two") {
                        testSuite("level three") {
                            test("leaf test") {
                                // passing
                            }
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
                assertTrue(
                    json.contains("\"name\":\"parentSuite\",\"value\":\"suiteLabelsSuite\""),
                    "the top-level suite becomes parentSuite (the JUnit4 'class' node)"
                )
                assertTrue(
                    json.contains("\"name\":\"suite\",\"value\":\"level one\""),
                    "the first level below the top-level suite becomes suite (space un-escaped)"
                )
                assertTrue(
                    json.contains("\"name\":\"subSuite\",\"value\":\"level two > level three\""),
                    "remaining levels join into subSuite, excluding the leaf test"
                )
                assertTrue(
                    json.contains("\"name\":\"package\",\"value\":\"suiteLabelsSuite\""),
                    "the top-level suite name becomes the package label (Packages view)"
                )
                assertTrue(
                    json.contains("\"name\":\"testClass\",\"value\":\"suiteLabelsSuite\""),
                    "the top-level suite name becomes the testClass label"
                )
                assertTrue(
                    json.contains("\"name\":\"testMethod\",\"value\":\"leaf test\""),
                    "the leaf name becomes the testMethod label"
                )
                assertTrue(json.contains("\"name\":\"host\""), "host label recorded automatically")
                assertTrue(json.contains("\"name\":\"thread\""), "thread label recorded automatically")
                resultsDir.deleteRecursively()
            }
        }

    @Test
    fun `a test directly under the top-level suite gets a suite label only, like allure-junit5`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-suite-single").toFile()

            val suite by testSuite(
                qualifiedPropertyName = "suiteSingleLabelSuite",
                testConfig =
                TestConfig
                    .allure(epic = "Shop", feature = "Checkout", "Checkout → Receipt")
                    .executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                test("direct test") {
                    // passing
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
                    json.contains("\"name\":\"suite\",\"value\":\"suiteSingleLabelSuite\""),
                    "an unnested chain maps the top-level suite to the suite label"
                )
                assertTrue(!json.contains("\"name\":\"parentSuite\""), "no parentSuite label without nesting")
                assertTrue(!json.contains("\"name\":\"subSuite\""), "no subSuite label without deeper levels")
                resultsDir.deleteRecursively()
            }
        }

    @Test
    fun `a plain test gets a clean leaf name and path-based fullName`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-plain-name").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "plainNameSuite",
            testConfig =
            TestConfig
                .allure(epic = "Shop", feature = "Checkout", "Checkout → Receipt")
                .executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            testSuite("plain group") {
                test("plain leaf") {
                    // passing
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
            assertTrue(
                json.contains("\"name\":\"plain leaf\""),
                "name falls back to the un-escaped leaf path segment, not the internal ID"
            )
            assertTrue(
                json.contains("\"fullName\":\"plainNameSuite > plain group > plain leaf\""),
                "fullName falls back to the un-escaped path chain"
            )
            assertTrue(
                Regex("\"historyId\":\"[0-9a-f]{32}\"").containsMatchIn(json),
                "historyId is an md5 hash of the path segments, like allure-junit4's"
            )
            assertTrue(!json.contains(' '), "no NBSP (internal space escaping) leaks into the result")
            assertTrue(!json.contains('↘'), "no internal path separator leaks into the result")
            resultsDir.deleteRecursively()
        }
    }

    @Test
    fun `allureAttachment in a test body is written into the result`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-attach").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "attachSuite",
            testConfig =
            TestConfig
                .allure(epic = "Shop", feature = "Checkout", "Checkout → Receipt")
                .executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            test("attaches a log") {
                allureAttachment("server-response", "{\"ok\":true}", "application/json", fileExtension = "json")
            }
        }

        FrameworkTestUtilities.withTestReport(suite) {
            val json =
                resultsDir
                    .listFiles { f -> f.name.endsWith("-result.json") }
                    .orEmpty()
                    .first()
                    .readText()
            assertTrue(json.contains("\"name\":\"server-response\""), "attachment entry present")
            assertTrue(json.contains("\"type\":\"application/json\""), "attachment type present")
            // the referenced attachment FILE exists in the results dir
            val attachmentFiles = resultsDir.listFiles { f -> f.name.endsWith("-attachment.json") }.orEmpty()
            assertTrue(attachmentFiles.isNotEmpty(), "attachment file written")
            assertTrue(attachmentFiles.first().readText().contains("\"ok\":true"), "attachment content written")
            resultsDir.deleteRecursively()
        }
    }

    @Test
    fun `custom sink receives result json and attachment files`() = FrameworkTestUtilities.withTestFramework {
        val files = mutableMapOf<String, ByteArray>()

        val suite by testSuite(
            qualifiedPropertyName = "sinkSuite",
            testConfig =
            TestConfig
                .executionReport(
                    AllureExecutionReport(
                        AllureResultsSink { fileName, content ->
                            files[fileName] =
                                content
                        }
                    )
                )
        ) {
            test("writes through the sink") {
                allureAttachment("note", "sink says hello", "text/plain")
            }
        }

        FrameworkTestUtilities.withTestReport(suite) {
            val resultJson =
                files.entries
                    .single { it.key.endsWith("-result.json") }
                    .value
                    .decodeToString()
            assertTrue(resultJson.contains("\"writes through the sink\""), "result json goes through the sink")
            // without a fileExtension argument, the attachment file has no extension (like allure-java)
            val attachment =
                files.entries
                    .single { it.key.endsWith("-attachment") }
                    .value
                    .decodeToString()
            assertTrue(attachment.contains("sink says hello"), "attachment content goes through the sink")
        }
    }

    @Test
    fun `disabled test writes a skipped result`() = FrameworkTestUtilities.withTestFramework {
        val resultsDir = Files.createTempDirectory("allure-results-skipped").toFile()

        val suite by testSuite(
            qualifiedPropertyName = "skippedSuite",
            testConfig = TestConfig.executionReport(AllureExecutionReport(resultsDir.absolutePath))
        ) {
            test("is skipped", testConfig = TestConfig.disable()) {
                error("never runs")
            }
            test("runs") {
                // passing
            }
        }

        FrameworkTestUtilities.withTestReport(suite) {
            val jsons =
                resultsDir
                    .listFiles { f -> f.name.endsWith("-result.json") }
                    .orEmpty()
                    .map { it.readText() }
            val skipped = jsons.singleOrNull { it.contains("\"name\":\"is skipped\"") }
            assertNotNull(skipped, "a result json must be written for the disabled test")
            assertTrue(skipped.contains("\"status\":\"skipped\""), "disabled test recorded as skipped")
        }
        resultsDir.deleteRecursively()
    }

    @Test
    fun `a name containing the display separator cannot collide with a nested path in tracking ids`() =
        FrameworkTestUtilities.withTestFramework {
            val resultsDir = Files.createTempDirectory("allure-results-collision").toFile()

            // Both tests produce the same displayed fullName ("collisionSuite > outer > inner > leaf"),
            // but their tracking ids must differ.
            val suite by testSuite(
                qualifiedPropertyName = "collisionSuite",
                testConfig = TestConfig.executionReport(AllureExecutionReport(resultsDir.absolutePath))
            ) {
                testSuite("outer > inner") {
                    test("leaf") {
                        // passing
                    }
                }
                testSuite("outer") {
                    testSuite("inner") {
                        test("leaf") {
                            // passing
                        }
                    }
                }
            }

            FrameworkTestUtilities.withTestReport(suite) {
                val jsons =
                    resultsDir
                        .listFiles { f -> f.name.endsWith("-result.json") }
                        .orEmpty()
                        .map { it.readText() }
                assertEquals(2, jsons.size, "both tests write a result")
                val fullNames = jsons.map { Regex("\"fullName\":\"([^\"]*)\"").find(it)!!.groupValues[1] }
                val historyIds = jsons.map { Regex("\"historyId\":\"([^\"]*)\"").find(it)!!.groupValues[1] }
                assertEquals(fullNames[0], fullNames[1], "the displayed fullName is identical for both")
                assertTrue(historyIds[0] != historyIds[1], "tracking ids must not collide")
                assertTrue(historyIds.all { it.matches(Regex("[0-9a-f]{32}")) }, "tracking ids are md5 hex")
            }
            resultsDir.deleteRecursively()
        }

    @Test
    fun `reports configured at two hierarchy levels fail fast before any test runs`() =
        FrameworkTestUtilities.withTestFramework {
            val dirA = Files.createTempDirectory("allure-results-dup-a").toFile()
            val dirB = Files.createTempDirectory("allure-results-dup-b").toFile()

            // Models the session + suite misconfiguration: an outer report already covers the
            // subtree, an inner element configures another one.
            val suite by testSuite(
                qualifiedPropertyName = "duplicateReportSuite",
                testConfig = TestConfig.executionReport(AllureExecutionReport(dirA.absolutePath))
            ) {
                testSuite(
                    "inner suite",
                    testConfig = TestConfig.executionReport(AllureExecutionReport(dirB.absolutePath))
                ) {
                    test("never runs") {
                        // the duplicate report configuration must fail before any test executes
                    }
                }
            }

            FrameworkTestUtilities.withTestReport(suite, expectFrameworkFailure = true) { frameworkFailure ->
                val failure = frameworkFailure ?: finishedEvents().firstNotNullOfOrNull { it.throwable }
                assertNotNull(failure, "execution must fail on the duplicate report configuration")
                assertTrue(
                    failure.message.orEmpty().contains("Multiple AllureExecutionReports"),
                    "failure names the duplicate report configuration, was: ${failure.message}"
                )
                assertTrue(
                    dirA.listFiles().isNullOrEmpty() && dirB.listFiles().isNullOrEmpty(),
                    "no result may be written by either report"
                )
            }
            dirA.deleteRecursively()
            dirB.deleteRecursively()
        }
}
