import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    `java-library`
    jacoco
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
    id("jsonapi-java-spring-spotless")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withJavadocJar()
}

dependencies {
    compileOnly(libs.findLibrary("jspecify").get())
    errorprone(libs.findLibrary("errorprone-core").get())
    errorprone(libs.findLibrary("nullaway").get())
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

nullaway {
    onlyNullMarked.set(true)
    jspecifyMode.set(true)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named<JavaCompile>("compileJava") {
    options.errorprone {
        disableAllChecks.set(true)
        error("NullAway")
        error("RequireExplicitNullMarking")
        nullaway {
            error()
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

tasks.withType<Javadoc>().configureEach {
    exclude("**/internal/**")
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        addStringOption("tag", "apiNote:a:API Note:")
        addBooleanOption("Xdoclint:all,-missing", true)
    }
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// JaCoCo otherwise silently skips verification when no tests produce execution data.
val verifyCoverageData =
    tasks.register("verifyCoverageData") {
        dependsOn(tasks.test, tasks.classes)
        doLast {
            val verification = tasks.jacocoTestCoverageVerification.get()
            val hasClasses =
                sourceSets.main
                    .get()
                    .output.classesDirs.asFileTree.files
                    .any { it.extension == "class" }
            if (verification.enabled && hasClasses && verification.executionData.files.none { it.exists() }) {
                throw GradleException("Production classes require test coverage data; add tests before running check.")
            }
        }
    }

tasks.check {
    dependsOn(tasks.javadoc, tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification, verifyCoverageData)
}
