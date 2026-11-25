plugins {
    id("jsonapi-java-spring-library")
    alias(libs.plugins.cyclonedx)
    id("jsonapi-java-spring-publish")
}

description = "JSON:API WebMVC auto-configuration for Spring Boot with Jackson 3."

dependencies {
    api(platform(libs.boot.bom))
    api(project(":jsonapi-java-spring-webmvc"))
    api(libs.jsonapi.jackson3)
    api(libs.boot.webmvc)
    api(libs.boot.validation)
    testImplementation(libs.boot.test)
    testImplementation(libs.assertj)
    testImplementation(libs.spring.test)
}
