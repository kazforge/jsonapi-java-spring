plugins {
    id("jsonapi-java-spring-library")
    alias(libs.plugins.cyclonedx)
    id("jsonapi-java-spring-publish")
}

description = "Jackson-neutral JSON:API HTTP adaptation for Spring WebMVC."

val springTestVersion = providers.gradleProperty("springTestVersion").getOrElse("minimum")
require(springTestVersion in setOf("minimum", "current")) {
    "springTestVersion must be minimum or current"
}

dependencies {
    api(libs.jsonapi.api)
    api(libs.jsonapi.query)
    api(libs.spring.webmvc)
    api(libs.servlet.api)
    api(libs.validation.api)
    testImplementation(libs.spring.test)
    testImplementation(libs.hamcrest)
    testRuntimeOnly(libs.hibernate.validator)
    testRuntimeOnly(libs.expressly)
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
