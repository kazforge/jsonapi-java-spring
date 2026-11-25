plugins {
    application
    id("jsonapi-java-spring-library")
}

application {
    mainClass.set("com.kazforge.jsonapi.example.boot.ArticleApplication")
}

val current = providers.gradleProperty("springTestVersion").getOrElse("minimum") == "current"

dependencies {
    implementation(platform(if (current) libs.boot.current.bom else libs.boot.bom))
    implementation(project(":jsonapi-java-spring-boot-starter"))
    testImplementation(libs.boot.test)
    testImplementation(libs.spring.test)
    testImplementation(libs.hamcrest)
}

tasks.test {
    systemProperty(
        "boot.expectedVersion",
        if (current) {
            libs.versions.boot.current
                .get()
        } else {
            libs.versions.boot.minimum
                .get()
        },
    )
}
