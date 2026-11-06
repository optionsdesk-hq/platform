// A deployable Spring Boot app: the Java conventions plus Spring Boot and a Jib image for GHCR.
// Image: ghcr.io/optionsdesk-hq/<app>:<IMAGE_TAG> and :main (CI sets IMAGE_TAG to the commit sha).
plugins {
    id("optionsdesk.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.cloud.tools.jib")
}

jib {
    from { image = "eclipse-temurin:21-jre" }
    to {
        image = "ghcr.io/optionsdesk-hq/${project.name}"
        tags = setOf(System.getenv("IMAGE_TAG") ?: "dev", "main")
        auth {
            username = System.getenv("REGISTRY_USER") ?: ""
            password = System.getenv("REGISTRY_PASSWORD") ?: ""
        }
    }
    container {
        jvmFlags = listOf("-XX:MaxRAMPercentage=75", "-Duser.timezone=Asia/Kolkata")
        ports = listOf("8080")
        user = "1000:1000" // never run as root
        labels = mapOf("org.opencontainers.image.source" to "https://github.com/optionsdesk-hq/platform")
    }
}
