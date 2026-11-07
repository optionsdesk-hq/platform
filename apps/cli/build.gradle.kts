// "od": the OptionsDesk terminal console. A plain Java app over the trading API (no Spring):
// ./gradlew :apps:cli:installDist  ->  apps/cli/build/install/od/bin/od
plugins {
    id("optionsdesk.java-conventions")
    application
}

application {
    applicationName = "od"
    mainClass = "com.optionsdesk.cli.OdApplication"
}

dependencies {
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj)
}
