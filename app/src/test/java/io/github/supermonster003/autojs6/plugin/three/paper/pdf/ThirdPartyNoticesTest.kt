package io.github.supermonster003.autojs6.plugin.three.paper.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps `THIRD_PARTY_NOTICES.md` in step with `locks/runtime-dependencies.lock` (roadmap D42, P0.1). Gradle's
 * verifyRuntimeDependencyLock ties the lock to the resolved release classpath; this test ties the generated notices to
 * the lock, so `test` fails when the generator was not rerun.
 * zh-CN: 使 `THIRD_PARTY_NOTICES.md` 与 `locks/runtime-dependencies.lock` 同步 (路线图 D42, P0.1). Gradle 的
 * verifyRuntimeDependencyLock 把锁与解析出的 release 类路径绑定; 本测试把生成的声明与锁绑定, 未重跑生成器时 `test` 失败.
 */
class ThirdPartyNoticesTest {

    private val lock: Map<String, String> by lazy { LockFiles.read(RUNTIME_LOCK) - "format" }

    private val notices: String by lazy { RepositoryFiles.text("THIRD_PARTY_NOTICES.md") }

    @Test
    fun `every locked runtime artifact is listed once with its version`() {
        lock.forEach { (coordinate, version) ->
            assertTrue("THIRD_PARTY_NOTICES.md must list $coordinate $version", notices.contains("| `$coordinate` | $version |"))
        }
        val listed = Regex("""^\| `([^`/]+:[^`]+)` \| """, RegexOption.MULTILINE).findAll(notices).map { it.groupValues[1] }.toList()
        assertEquals(lock.keys.sorted(), listed.sorted())
        assertTrue(notices.contains("packages ${lock.size} Maven artifacts"))
    }

    @Test
    fun `the lock is sorted and holds release artifacts only`() {
        assertEquals(lock.keys.sorted(), lock.keys.toList())
        assertTrue("the Compose native library dependency (roadmap Q9)", lock.containsKey("androidx.graphics:graphics-path"))
        assertEquals("PDFBox-Android is pinned (roadmap Q1)", "2.0.27.0", lock["com.tom-roush:pdfbox-android"])
        lock.keys.forEach { coordinate ->
            assertFalse("$coordinate is a test or debug artifact", TEST_OR_DEBUG.any { coordinate.startsWith(it) })
            assertFalse("$coordinate: BOMs ship nothing", coordinate.endsWith("-bom"))
        }
    }

    @Test
    fun `the notices carry the Bouncy Castle licence and the PDFBox-Android data files`() {
        assertTrue(BOUNCY_CASTLE.all { lock.containsKey(it) })
        assertTrue(notices.contains("Copyright (c) 2000-2022 The Legion of the Bouncy Castle Inc."))
        assertTrue(notices.contains("## Data files bundled by PDFBox-Android"))
        listOf("LiberationSans-Regular.ttf", "glyphlist.txt", "fontbox/resources/cmap/").forEach {
            assertTrue("the bundled data table must name $it", notices.contains(it))
        }
        assertTrue(notices.contains("OFL-1.1"))
    }

    private companion object {
        const val RUNTIME_LOCK = "locks/runtime-dependencies.lock"
        val BOUNCY_CASTLE = listOf(
            "org.bouncycastle:bcpkix-jdk15to18", "org.bouncycastle:bcprov-jdk15to18", "org.bouncycastle:bcutil-jdk15to18",
        )
        val TEST_OR_DEBUG = listOf(
            "junit:", "org.hamcrest:", "androidx.test", "androidx.compose.ui:ui-test", "androidx.compose.ui:ui-tooling-android",
            "androidx.compose.ui:ui-tooling-data", "androidx.compose.ui:ui-tooling=",
        )
    }

}
