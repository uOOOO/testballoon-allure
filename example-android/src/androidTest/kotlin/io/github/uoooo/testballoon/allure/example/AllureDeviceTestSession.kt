package io.github.uoooo.testballoon.allure.example

import android.os.Build
import io.github.uoooo.testballoon.allure.android.AllureAndroidTestSession

/**
 * Activates Allure reporting for all TestBalloon device tests in this module. Results are stored
 * via TestStorage and retrieved by `connectedAndroidTest` into `build/outputs/`. The device facts
 * fill the report's Environment widget.
 */
class AllureDeviceTestSession : AllureAndroidTestSession(environment = deviceEnvironment())

/** The device facts shown in the report's Environment widget. */
private fun deviceEnvironment(): Map<String, String> = mapOf(
    "Brand" to Build.BRAND,
    "Model" to Build.MODEL,
    "SDK" to Build.VERSION.SDK_INT.toString(),
    "Release" to Build.VERSION.RELEASE
)
