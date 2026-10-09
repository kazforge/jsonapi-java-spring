plugins {
    base
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
