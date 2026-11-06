// The market-data app: records and imports (recorder, candle sync, NSE/BSE/FII imports, data quality).
// A separate process so a data job can never take the trading engine down (docs/adr/0006).
plugins {
    id("optionsdesk.spring-app")
}

dependencies {
    implementation(project(":libs:shared-kernel"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly(libs.junit.launcher)
}
