pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "distortion-waves"
include(":distortion-waves")
include(":sample:shared")
include(":sample:androidApp")
include(":sample:desktopApp")
