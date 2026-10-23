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
        // Legacy Xposed API 82 is compileOnly; it is NOT packed into the APK.
        maven("https://repository-cdn.liferay.com/nexus/content/repositories/gradle-plugins/")
    }
}
rootProject.name = "NiuEnhance"
include(":app")
