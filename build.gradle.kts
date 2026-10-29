buildscript {
    dependencies {
        constraints {
            classpath("com.fasterxml.jackson.core:jackson-core:2.22.4") {
                because("CycloneDX tooling must not resolve vulnerable jackson-core 2.22.2")
            }
            classpath("com.google.guava:guava:33.7.2-jre") {
                because("CycloneDX tooling must not resolve vulnerable Guava 33.7.1")
            }
        }
    }
}

plugins {
    base
    alias(libs.plugins.cyclonedx) apply false
    alias(libs.plugins.sonarqube)
    id("jsonapi-java-spring-spotless")
}

tasks.check {
    dependsOn("spotlessCheck", gradle.includedBuild("build-logic").task(":check"))
}

sonar {
    properties {
        property("sonar.projectKey", "kazforge_jsonapi-java-spring")
        property("sonar.organization", "kazemek")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.qualitygate.wait", "true")
        // Consume the existing library convention's XML reports, including future modules.
        property("sonar.coverage.jacoco.xmlReportPaths", "$rootDir/**/build/reports/jacoco/test/jacocoTestReport.xml")
    }
}
