# testballoon-allure

[![CI](https://github.com/uOOOO/testballoon-allure/actions/workflows/ci.yml/badge.svg)](https://github.com/uOOOO/testballoon-allure/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

[Allure](https://allurereport.org/) reporting for the [TestBalloon](https://github.com/infix-de/testBalloon)
test framework — DSL-only, with capability parity to `allure-junit4`.

| Module | Use for |
|---|---|
| `testballoon-allure` | JVM and Android unit (Robolectric) tests |
| `testballoon-allure-android` | Android instrumented (device) tests, writing results via TestStorage |

## Quick start

```kotlin
// build.gradle.kts
plugins {
    id("de.infix.testBalloon") version "<testballoon-version>"
}

dependencies {
    testImplementation("io.github.uoooo:testballoon-allure:<version>")
}
```

```kotlin
// Activate reporting for the whole test module:
class MyTestSession : AllureTestSession()

// Annotate tests with Allure metadata and runtime steps:
val mySuite by testSuite(
    "checkout",
    testConfig = TestConfig.allure {
        epic("Shop")
        feature("Checkout")
        severity(AllureSeverity.CRITICAL)
    }
) {
    test("pays with a saved card") {
        allureStep("prepare cart") { /* ... */ }
        allureAttachment("receipt", receiptJson, "application/json", fileExtension = "json")
    }
}
```

Results are written to `allure.results.directory` (default `allure-results`); generate a report with the
[Allure CLI](https://allurereport.org/docs/install/): `allure serve <results-directory>`.

By default the Suites view carries a single `suite` label naming the top-level suite — what
allure-junit4 emits for the test class; nested structure stays in fullName and the step chain.
Declaring `suite`/`parentSuite`/`subSuite` explicitly replaces the derived label — like
allure-pytest's suite decorators:

```kotlin
testConfig = TestConfig.allure {
    suite("Shop")
    subSuite("Checkout")
}
```

`displayName("…")` overrides a test's list entry the same way (the `@DisplayName` equivalent of
allure-junit4), leaving fullName, the step chain and history tracking untouched.

## Robolectric (host) tests

Use the plain `testballoon-allure` module together with TestBalloon's Robolectric integration — with an
`AllureTestSession` subclass in place (see above), no extra setup is needed:

```kotlin
val mySuite by testSuite(
    testConfig = TestConfig
        .robolectric { sdk = 34 }
        .allure { epic("Shop") }
) {
    robolectricTestSuite<MySuiteContent>("on Android 14")
}
```

The session automatically keeps this integration's classes host-loaded (out of the Robolectric sandbox),
so runtime steps and attachments recorded inside the sandbox reach the report. Only when you skip the
session and wire an `AllureExecutionReport` onto individual suites instead, add the setting yourself:

```kotlin
.robolectric { portablePackages += "io.github.uoooo.testballoon.allure" }
```

## Android instrumented (device) tests

`testballoon-allure-android` writes results on the device via
[TestStorage](https://developer.android.com/reference/androidx/test/services/storage/TestStorage), so Gradle
retrieves them to the host automatically.

```kotlin
// build.gradle.kts (Android module)
plugins {
    id("de.infix.testBalloon") version "<testballoon-version>"
}

android {
    defaultConfig {
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
    }
}

dependencies {
    androidTestImplementation("io.github.uoooo:testballoon-allure-android:<version>")
    androidTestUtil("androidx.test.services:test-services:<androidx-test-services-version>")
}
```

```kotlin
// androidTest source set — activates reporting for all instrumented TestBalloon tests:
class MyDeviceTestSession : AllureAndroidTestSession()
```

After `connectedAndroidTest`, results appear under
`build/outputs/connected_android_test_additional_output/<variant>/connected/<device>/allure-results`.

## Status

Work in progress — not published yet.

## License

[Apache-2.0](LICENSE)
