enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "autojs6-plugin-three-paper-pdf"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("io.github.supermonster003.autojs6-platform-versions") version "1.9.1"
        // The shared 16 KB page alignment gate (roadmap D26, appendix D Q9): PDFBox-Android and Bouncy Castle are pure
        // Java, but Compose ui-graphics brings libandroidx.graphics.path.so.
        id("io.github.supermonster003.autojs6-native-alignment") version "1.9.1"
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

plugins {
    id("io.github.supermonster003.autojs6-platform-versions")
    id("org.gradle.toolchains.foojay-resolver-convention")
}

includeBuild("build-logic")
include(":app")
