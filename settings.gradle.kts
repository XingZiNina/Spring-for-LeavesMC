pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.leavesmc.org/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

rootProject.name = "SpringLeaves"

include("leaves-api", "leaves-server")
