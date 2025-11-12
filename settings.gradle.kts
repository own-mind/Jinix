rootProject.name = "jinix-plugin"
include("TestModule")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}