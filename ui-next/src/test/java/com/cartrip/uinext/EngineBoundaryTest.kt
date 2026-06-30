package com.cartrip.uinext

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural guard: `:ui-next` may reach the engine ONLY through `com.cartrip.engine.api.*`. It must not
 * import legacy/internal engine packages (`data` / `cloud` / `record` / `export` / `settings`) or the legacy
 * `ui` package / `MainActivity` / `TripApp`.
 *
 * While legacy `:app` still imports engine internals the boundary cannot be compiler-enforced (`internal` is
 * module-scoped, and the engine is one module), so this source scan IS the enforcement. Konsist-lite - swap
 * for Konsist if richer rules are wanted later.
 */
class EngineBoundaryTest {

    private val forbiddenPackages = Regex(
        "^\\s*import\\s+com\\.cartrip\\.analyzer\\.(data|cloud|record|export|settings|ui)\\."
    )
    private val forbiddenExact = listOf(
        "com.cartrip.analyzer.MainActivity",
        "com.cartrip.analyzer.TripApp",
    )

    @Test
    fun ui_next_reaches_the_engine_only_via_engine_api() {
        val srcDir = locateMainSrc()
        val ktFiles = srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("No :ui-next sources found under $srcDir - the guard cannot run.", ktFiles.isNotEmpty())

        val violations = buildList {
            for (file in ktFiles) {
                file.readLines().forEachIndexed { idx, line ->
                    val bad = forbiddenPackages.containsMatchIn(line) ||
                        forbiddenExact.any { line.contains("import $it") }
                    if (bad) add("${file.name}:${idx + 1}  ${line.trim()}")
                }
            }
        }

        assertTrue(
            ":ui-next must access the engine only through com.cartrip.engine.api.*. Forbidden imports:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /** Test workingDir is normally the module dir; fall back to the repo-root layout. */
    private fun locateMainSrc(): File {
        val userDir = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(userDir, "src/main/java/com/cartrip/uinext"),
            File(userDir, "ui-next/src/main/java/com/cartrip/uinext"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate :ui-next main sources (user.dir=$userDir)")
    }
}
