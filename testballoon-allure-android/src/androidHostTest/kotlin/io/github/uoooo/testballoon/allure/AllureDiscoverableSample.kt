package io.github.uoooo.testballoon.allure

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.executionReport
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import testing.integration.allure.FullFeatureSampleContent
import testing.integration.allure.MultiDepthSampleContent
import testing.integration.allure.SdkCheckSampleContent

/**
 * Operational-path proof: a compiler-plugin-discoverable top-level testSuite that, when `:check`
 * runs, writes a real Allure result JSON to `build/allure-results` via the `allure.results.directory`
 * system property injected by this module's build.gradle.kts. The report itself is wired module-wide
 * by [AllureSampleSession].
 *
 * Contrast with AllureRobolectricCompositionTest, which controls the results directory explicitly and
 * asserts on the written JSON; this suite validates the full Gradle-driven path end-to-end (no assertions).
 */
val AllureDiscoverableSample by testSuite(
    testConfig =
    TestConfig
        // SdkCheckSampleContent asserts SDK_INT == 34 — keep these two values in sync.
        // No portablePackages needed here: AllureSampleSession (an AllureTestSession) injects it
        // session-wide — this sample also proves that auto-injection.
        .robolectric { sdk = 34 }
        // labels mirror AllureRobolectricCompositionTest (epic/feature/story) — keep in sync
        .allure {
            epic("Platform")
            feature("Sandbox")
            story("SDK version check")
            severity(AllureSeverity.NORMAL)
            owner("testBalloon")
        }
) {
    robolectricTestSuite<SdkCheckSampleContent>("SdkCheck discoverable")
    robolectricTestSuite<MultiDepthSampleContent>("MultiDepth discoverable")
    robolectricTestSuite<FullFeatureSampleContent>("FullFeature discoverable")
}

/**
 * Twin of the SdkCheck registration above, differing only in explicit Suites-view labels: in the
 * report's Suites tab this suite appears under Platform > Sandbox > SDK checks, while the twin
 * stays under the path-derived grouping — a side-by-side view of the override.
 */
val AllureExplicitSuitesSample by testSuite(
    testConfig =
    TestConfig
        .robolectric { sdk = 34 }
        .allure {
            epic("Platform")
            feature("Sandbox")
            story("SDK version check")
            severity(AllureSeverity.NORMAL)
            owner("testBalloon")
            parentSuite("Platform")
            suite("Sandbox")
            subSuite("SDK checks")
        }
) {
    robolectricTestSuite<SdkCheckSampleContent>("SdkCheck discoverable (explicit suites)")
}
