package io.github.uoooo.testballoon.allure.example

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import io.github.uoooo.testballoon.allure.allure
import io.github.uoooo.testballoon.allure.allureAttachment
import io.github.uoooo.testballoon.allure.allureStep

/**
 * Verifies that the Allure integration runs inside an instrumented test — allure-java on ART.
 * [AllureDeviceTestSession] activates recording; `connectedAndroidTest` retrieves the results
 * into `build/outputs/`.
 */
val AllureDeviceTest by testSuite(
    testConfig = TestConfig
        .allure {
            epic("Device")
            feature("Allure on-device reporting")
        }
) {
    testSuite("device group") {
        test("writes an allure result on the device") {
            allureStep("compute") {
                allureAttachment(
                    "device-log",
                    "sdk=${android.os.Build.VERSION.SDK_INT}",
                    "text/plain",
                    fileExtension = "txt"
                )
                check(2 + 2 == 4)
            }
        }
    }
}

/**
 * Twin of [AllureDeviceTest] differing only in explicit Suites-view labels: in the report's
 * Suites tab this suite appears under Device examples > Smoke, while the twin stays under the
 * path-derived grouping — a side-by-side view of the override.
 */
val AllureDeviceExplicitSuitesTest by testSuite(
    testConfig = TestConfig
        .allure {
            epic("Device")
            feature("Allure on-device reporting")
            suite("Device examples")
            subSuite("Smoke")
        }
) {
    testSuite("device group") {
        test("writes an allure result under explicit suites") {
            allureStep("compute") {
                allureAttachment(
                    "device-log",
                    "sdk=${android.os.Build.VERSION.SDK_INT}",
                    "text/plain",
                    fileExtension = "txt"
                )
                check(2 + 2 == 4)
            }
        }
    }
}
