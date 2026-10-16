plugins {
    id("jsonapi-java-spring-library")
}

// A non-published application example, deliberately independent of springTestVersion.
dependencies {
    implementation(project(":jsonapi-java-spring-webmvc"))
    implementation(libs.jsonapi.jackson3)
    implementation(platform(libs.spring.current.bom))
    testImplementation(libs.spring.test)
    testImplementation(libs.hamcrest)
}

tasks.test {
    systemProperty(
        "spring.consumerVersion",
        libs.versions.spring.current
            .get(),
    )
}
