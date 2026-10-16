plugins {
    id("jsonapi-java-spring-library")
    alias(libs.plugins.cyclonedx)
    id("jsonapi-java-spring-publish")
}

tasks.named<org.cyclonedx.gradle.CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs = listOf("compileClasspath", "runtimeClasspath")
    includeMetadataResolution = false
    xmlOutput.unsetConvention()
    jsonOutput.set(layout.buildDirectory.file("reports/release/bom.json"))
}

val springTestVersion = providers.gradleProperty("springTestVersion").getOrElse("minimum")
require(springTestVersion in setOf("minimum", "current")) {
    "springTestVersion must be minimum or current"
}

dependencies {
    api(libs.jsonapi.api)
    api(libs.jsonapi.query)
    api(libs.spring.webmvc)
    api(libs.servlet.api)
    testImplementation(libs.spring.test)
    testImplementation(libs.hamcrest)
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
