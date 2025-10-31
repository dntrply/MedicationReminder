pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MedicationReminder"
include(":app")
include(":whisper")
project(":whisper").projectDir = file("external/whisper.cpp/examples/whisper.android/lib")
