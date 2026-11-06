// Pure Java: domain types, pricing and the market calendar. No Spring, no I/O; depended on by every app.
plugins {
    id("optionsdesk.java-conventions")
    `java-library`
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj)
    testImplementation(libs.jqwik)
    testImplementation(libs.archunit)
    testImplementation(libs.jackson.databind) // reads the golden vectors
}
