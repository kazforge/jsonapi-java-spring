plugins {
    base
    id("jsonapi-java-spring-spotless")
}

tasks.check {
    dependsOn("spotlessCheck", gradle.includedBuild("build-logic").task(":check"))
}
