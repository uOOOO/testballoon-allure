package io.github.uoooo.testballoon.allure.example

import io.github.uoooo.testballoon.allure.android.AllureAndroidTestSession

/**
 * Activates Allure reporting for all TestBalloon device tests in this module. Results are stored
 * via TestStorage and retrieved by `connectedAndroidTest` into `build/outputs/`.
 */
class AllureDeviceTestSession : AllureAndroidTestSession()
