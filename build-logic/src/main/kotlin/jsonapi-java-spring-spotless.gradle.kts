plugins {
    id("com.diffplug.spotless")
}

spotless {
    java {
        target("**/*.java")
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
