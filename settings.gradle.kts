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
rootProject.name = "MediaStorm"
// :core is the shared logic (API, stores, player engine, iroh, Dolby Vision); :app is the
// Android TV app (com.strmr.tv); :mobile is the phone and tablet app.
include(":core", ":app")
