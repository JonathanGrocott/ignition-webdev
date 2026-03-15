pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://nexus.inductiveautomation.com/repository/inductiveautomation-releases/")
        }
    }
}

rootProject.name = "ignition-web"

include(
    "webcompat-common",
    "webcompat-gateway",
    "webcompat-designer",
    "webcompat-build"
)
