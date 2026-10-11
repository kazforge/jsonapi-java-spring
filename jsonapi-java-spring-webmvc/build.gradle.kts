plugins {
    id("jsonapi-java-spring-library")
}

val springTestVersion = providers.gradleProperty("springTestVersion").getOrElse("minimum")
require(springTestVersion in setOf("minimum", "current")) {
    "springTestVersion must be minimum or current"
}

dependencies {
    api(libs.jsonapi.api)
    api(libs.spring.webmvc)
    if (springTestVersion == "current") {
        testImplementation(platform(libs.spring.current.bom))
    }
}

tasks.test {
    systemProperty(
        "spring.expectedVersion",
        if (springTestVersion == "current") {
            libs.versions.spring.current
                .get()
        } else {
            libs.versions.spring.minimum
                .get()
        },
    )
}
