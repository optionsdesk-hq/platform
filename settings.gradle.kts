pluginManagement {
    includeBuild("build-logic") // convention plugins: one place for Java, formatting, tests, coverage, images
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "optionsdesk-platform"

include(
    "libs:shared-kernel", // pure domain, pricing, calendar: no Spring, no I/O
    "apps:trading", //       decides and trades (Spring Boot, Spring Modulith)
    "apps:marketdata", //    records and imports (Spring Boot)
)
