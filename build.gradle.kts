buildscript {
    dependencies {
        constraints {
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

subprojects {
    pluginManager.withPlugin("org.cyclonedx.bom") {
        tasks.named<org.cyclonedx.gradle.CyclonedxDirectTask>("cyclonedxDirectBom") {
            includeConfigs = listOf("compileClasspath", "runtimeClasspath")
            includeMetadataResolution = false
            xmlOutput.unsetConvention()
            jsonOutput.set(layout.buildDirectory.file("reports/release/bom.json"))
        }
    }
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
