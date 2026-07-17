package io.github.uoooo.testballoon.allure

/**
 * Fails loudly when this library has been loaded by the Robolectric sandbox classloader.
 *
 * Sandbox-loaded copies of this library keep their own static state, so anything they record
 * (steps, attachments, parameters, config metadata) never reaches the host-side
 * [AllureExecutionReport] — data would vanish silently. The classes must stay host-loaded
 * ("portable" in Robolectric terms) instead. [AllureTestSession] arranges that automatically;
 * without a session, consumers add the `portablePackages` setting themselves.
 */
internal object RobolectricSandboxGuard {

    /** Robolectric's instrumenting classloader lives in `org.robolectric.*`; any other loader is fine. */
    private val isSandboxLoaded: Boolean = generateSequence(
        RobolectricSandboxGuard::class.java.classLoader?.javaClass as Class<*>?
    ) { it.superclass }
        .any { it.name.startsWith("org.robolectric.") }

    fun ensurePortable() {
        check(!isSandboxLoaded) {
            "The TestBalloon Allure integration was loaded inside the Robolectric sandbox, so its" +
                " recordings cannot reach the report. Either activate reporting with an AllureTestSession" +
                " subclass (it keeps this library host-loaded automatically), or add" +
                " robolectric { portablePackages += \"io.github.uoooo.testballoon.allure\" }" +
                " to the test configuration."
        }
    }
}
