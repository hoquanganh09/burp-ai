package com.burpai.agents

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AgentProfileLoaderTest {
    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("agent-profiles-test")
        AgentProfileLoader.setBaseDirForTests(tempDir)
    }

    @AfterEach
    fun tearDown() {
        AgentProfileLoader.setBaseDirForTests(null)
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `installs bundled profiles when directory is empty`() {
        val profiles = AgentProfileLoader.listAvailableProfiles()
        assertTrue(profiles.any { it.equals("pentester", ignoreCase = true) })
        assertTrue(profiles.any { it.equals("bughunter", ignoreCase = true) })
        assertTrue(profiles.any { it.equals("auditor", ignoreCase = true) })
    }

    @Test
    fun `discovers custom profiles`() {
        val custom = tempDir.resolve("custom.md")
        custom.writeText("[GLOBAL]\nCustom profile")
        val profiles = AgentProfileLoader.listAvailableProfiles()
        assertTrue(profiles.any { it.equals("custom", ignoreCase = true) })
    }
}

