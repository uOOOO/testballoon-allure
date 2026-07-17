package io.github.uoooo.testballoon.allure

/**
 * The module-wide session activating Allure reporting for every discoverable suite in this source
 * set — the single line a consuming module needs (see [AllureTestSession]). Also serves as the
 * end-to-end proof that the compiler plugin discovers an indirect [TestSession] subclass provided
 * by a library.
 */
class AllureSampleSession : AllureTestSession()
