pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

plugins {
    // Provisions missing JDK toolchains automatically.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }

    // Kotlin variant selection: -PkotlinVariant=<kotlin-version> repins the
    // catalog's kotlin and testballoon entries, so one branch can build and
    // publish every supported variant. See RELEASING.md.
    versionCatalogs {
        create("libs") {
            // Must track the TestBalloon version in gradle/libs.versions.toml.
            val testBalloonBaseVersion = "1.0.1"
            providers.gradleProperty("kotlinVariant").orNull?.let { kotlinVersion ->
                version("kotlin", kotlinVersion)
                version("testballoon", "$testBalloonBaseVersion-K$kotlinVersion")
            }
        }
    }
}

rootProject.name = "testballoon-allure"

include(":testballoon-allure")
include(":testballoon-allure-android")
include(":example-android")
