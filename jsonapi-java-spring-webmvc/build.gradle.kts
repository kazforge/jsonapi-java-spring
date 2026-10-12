plugins {
    id("jsonapi-java-spring-library")
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

val verifyNeutralDependencies =
    tasks.register("verifyNeutralDependencies") {
        doLast {
            listOf("compileClasspath", "runtimeClasspath", "testRuntimeClasspath").forEach { name ->
                val root =
                    configurations
                        .getByName(name)
                        .incoming.resolutionResult.rootComponent
                        .get()
                        .id
                configurations.getByName(name).incoming.resolutionResult.allComponents.forEach { component ->
                    val id = component.id
                    if (id is org.gradle.api.artifacts.component.ModuleComponentIdentifier) {
                        check(
                            !id.group.startsWith("com.fasterxml.jackson") &&
                                !id.group.startsWith("tools.jackson") &&
                                id.group != "org.springframework.boot" &&
                                !(id.group == "com.kazforge" && id.module.contains("jackson")),
                        ) { "Non-neutral dependency in $name: $id" }
                    } else {
                        check(id == root) { "Non-released dependency in $name: $id" }
                    }
                }
            }
        }
    }

tasks.check { dependsOn(verifyNeutralDependencies) }
