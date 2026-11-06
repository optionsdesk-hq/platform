// The trading app: decides and trades. A Spring Modulith modular monolith (see docs/adr/0006).
plugins {
    id("optionsdesk.spring-app")
}

dependencies {
    implementation(project(":libs:shared-kernel"))
    implementation(platform(libs.spring.modulith.bom))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.spring.modulith.core)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.spring.modulith.test)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(libs.archunit)
    testRuntimeOnly(libs.junit.launcher)
}
