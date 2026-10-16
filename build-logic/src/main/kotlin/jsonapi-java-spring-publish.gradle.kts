plugins {
    `java-library`
    `maven-publish`
    signing
}

java {
    withSourcesJar()
}

val releaseSbom = tasks.named("cyclonedxDirectBom")

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(layout.buildDirectory.file("reports/release/bom.json")) {
                classifier = "cyclonedx"
                extension = "json"
                builtBy(releaseSbom)
            }
            pom {
                name.set(project.name)
                description.set("Jackson-neutral JSON:API HTTP adaptation for Spring WebMVC.")
                url.set("https://github.com/kazforge/jsonapi-java-spring")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("kazforge")
                        name.set("kazforge")
                        url.set("https://github.com/kazforge")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/kazforge/jsonapi-java-spring.git")
                    developerConnection.set("scm:git:ssh://git@github.com/kazforge/jsonapi-java-spring.git")
                    url.set("https://github.com/kazforge/jsonapi-java-spring")
                }
            }
        }
    }
    repositories {
        maven {
            name = "centralStaging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

signing {
    val key = providers.gradleProperty("signingKey")
    isRequired = key.isPresent
    if (key.isPresent) {
        useInMemoryPgpKeys(key.get(), providers.gradleProperty("signingPassword").orElse("").get())
    }
    sign(publishing.publications["mavenJava"])
}
