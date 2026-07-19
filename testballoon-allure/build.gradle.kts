import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.vanniktech.maven.publish)
}

description = "Allure reporting for the TestBalloon test framework"

kotlin {
    explicitApi()
    jvmToolchain(17)

    // Pure-JVM module: Android consumers (device and Robolectric tests) use the jvm variant.
    // Android-specific additions (TestStorage) live in testballoon-allure-android.
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.testballoon.framework.core)
            }
        }

        jvmMain {
            dependencies {
                // implementation (not api): all Allure types are encapsulated inside AllureExecutionReport;
                // consumers use only TestConfig.allure(...) and AllureExecutionReport, which expose no Allure types.
                implementation(libs.allure.java.commons)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
