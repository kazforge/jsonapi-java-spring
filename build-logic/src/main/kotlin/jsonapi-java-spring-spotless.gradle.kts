import com.diffplug.spotless.LineEnding

plugins {
    id("com.diffplug.spotless")
}

spotless {
    lineEndings = LineEnding.UNIX
    java {
        // Each module owns its Java sources; overlapping root/module targets corrupt cached paths.
        target("src/**/*.java")
        targetExclude("**/build/**", "**/bin/**", "**/.gradle/**")
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/bin/**", "**/.gradle/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/bin/**", "**/.gradle/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

plugins.withId("base") {
    tasks.named("check") {
        dependsOn("spotlessCheck")
    }
}
