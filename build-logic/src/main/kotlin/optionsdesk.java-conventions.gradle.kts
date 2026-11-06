// Every Java module: Java 21, google-java-format, JUnit 5, and a coverage gate that fails the build.
plugins {
    java
    jacoco
    id("com.diffplug.spotless")
}

group = "com.optionsdesk"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror", "-parameters"))
    options.encoding = "UTF-8"
}

spotless {
    java {
        googleJavaFormat("1.22.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("failed") }
}

// Coverage gate: 85% of lines in each module. Spring Boot entry points (main methods) are excluded.
val coverageExclusions = listOf("**/*Application*")

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required = true; html.required = true }
    classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }))
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }))
    violationRules { rule { limit { counter = "LINE"; minimum = "0.85".toBigDecimal() } } }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
