// Convention plugins: every module applies one of these instead of repeating build configuration.
plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.3.5")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.6")
    implementation("com.google.cloud.tools:jib-gradle-plugin:3.4.4")
}
