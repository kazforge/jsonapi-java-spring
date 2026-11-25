pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "jsonapi-java-spring"
include("jsonapi-java-spring-webmvc")
include("jsonapi-java-spring-boot-starter")
include("examples:jackson3-webmvc")
include("examples:boot-webmvc")
