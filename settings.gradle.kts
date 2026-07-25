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
            providers.gradleProperty("kotlinVariant").orNull?.let { kotlinVersion ->
                // Base version taken from the catalog's testballoon pin, minus its -K suffix.
                val testBalloonPin = Regex("""^testballoon = "([^"]+)"""", RegexOption.MULTILINE)
                    .find(settingsDir.resolve("gradle/libs.versions.toml").readText())
                    ?.groupValues
                    ?.get(1)
                    ?: error("testballoon version not found in gradle/libs.versions.toml")
                version("kotlin", kotlinVersion)
                version("testballoon", "${testBalloonPin.substringBeforeLast("-K")}-K$kotlinVersion")
            }
        }
    }
}

rootProject.name = "testballoon-allure"

include(":testballoon-allure")
include(":testballoon-allure-android")
include(":example-android")
