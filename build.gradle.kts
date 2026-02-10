import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.burpai"
version = "0.1.0"

val releaseSummary = (project.findProperty("releaseSummary") as String?)?.trim().orEmpty()
val buildMetaStateFile = rootProject.file("build-meta.properties")
val generatedBuildInfoDir = layout.buildDirectory.dir("generated/resources/buildInfo")

application {
    applicationName = "BurpAI"
    mainClass = "com.burpai.BurpAiAgentExtension"
}

repositories {
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {
    // Burp Montoya API (compileOnly, Burp provides it at runtime)
    compileOnly("net.portswigger.burp.extensions:montoya-api:2025.12")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // HTTP client (Ollama + webhooks)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // MCP Server (Ktor + MCP SDK)
    implementation("io.modelcontextprotocol:kotlin-sdk:0.5.0")
    implementation("io.ktor:ktor-server-core:3.1.3")
    implementation("io.ktor:ktor-server-netty:3.1.3")
    implementation("io.ktor:ktor-server-cors:3.1.3")
    implementation("io.ktor:ktor-server-sse:3.1.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.1.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.5.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Logging façade (we keep it minimal; Burp logs are also used)
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("net.portswigger.burp.extensions:montoya-api:2025.12")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val prepareBuildInfo by tasks.registering {
    notCompatibleWithConfigurationCache("Writes local build metadata state file on each build.")
    outputs.dir(generatedBuildInfoDir)
    outputs.upToDateWhen { false }
    inputs.property("releaseSummary", releaseSummary)

    doLast {
        fun sanitize(value: String): String = value.replace(Regex("[\\r\\n]+"), " ").trim()

        val state = Properties()
        if (buildMetaStateFile.exists()) {
            buildMetaStateFile.inputStream().use(state::load)
        } else {
            state.setProperty("buildNumber", "-1")
            state.setProperty("lastSummary", "Initial baseline build.")
        }

        val previousNumber = state.getProperty("buildNumber")?.toIntOrNull() ?: -1
        val previousSummary = sanitize(state.getProperty("lastSummary").orEmpty())
            .ifBlank { "No previous summary." }
        val currentSummary = sanitize(releaseSummary).ifBlank { previousSummary }
        val buildNumber = previousNumber + 1
        val buildTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val outDir = generatedBuildInfoDir.get().asFile
        outDir.mkdirs()

        val buildInfo = Properties().apply {
            setProperty("buildTime", buildTime)
            setProperty("buildNumber", buildNumber.toString())
            setProperty("previousSummary", previousSummary)
            setProperty("currentSummary", currentSummary)
        }
        outDir.resolve("build-info.properties").outputStream().use { buildInfo.store(it, "Generated build info") }

        state.setProperty("buildNumber", buildNumber.toString())
        state.setProperty("lastSummary", currentSummary)
        buildMetaStateFile.outputStream().use { state.store(it, "BurpAI local build metadata") }
    }
}

sourceSets {
    named("main") {
        resources.srcDir(generatedBuildInfoDir)
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(listOf("-Xjsr305=strict"))
    }
}

tasks.processResources {
    dependsOn(prepareBuildInfo)
}

tasks.shadowJar {
    archiveBaseName.set("BurpAI")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.jar {
    archiveBaseName.set("BurpAI")
    archiveClassifier.set("plain")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}
