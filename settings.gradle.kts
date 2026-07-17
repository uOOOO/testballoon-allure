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
}

rootProject.name = "testballoon-allure"

include(":testballoon-allure")
include(":testballoon-allure-android")
include(":example-android")
