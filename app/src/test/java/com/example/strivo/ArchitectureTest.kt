package com.example.strivo

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the source tree in the shape described in docs/ARCHITECTURE.md. It reads the imports of every main source file,
 * so a rule is broken the moment a wrong import is written, not when someone next reads the code.
 */
class ArchitectureTest {
    private val root = File("src/main/java/com/example/strivo")
    private val base = "com.example.strivo"

    private class Source(val file: File, val path: String, val pkg: String, val imports: List<String>, val lines: Int) {
        /** domain, core, data.local, feature.home, navigation ... */
        val layer: String get() = layerOfPath(path)
    }

    private val sources: List<Source> by lazy {
        check(root.isDirectory) { "Run from the app module: ${root.absolutePath} not found" }
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { file ->
            val text = file.readText()
            Source(
                file = file,
                path = file.relativeTo(root).invariantSeparatorsPath,
                pkg = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1).orEmpty(),
                imports = Regex("""^import\s+([\w.]+)""", RegexOption.MULTILINE).findAll(text).map { it.groupValues[1] }.toList(),
                lines = text.lines().size,
            )
        }.toList()
    }

    /** The layer a file or an import of ours belongs to. Resources (R) and the app classes at the root are "app". */
    private fun layerOfImport(import: String): String? {
        if (!import.startsWith("$base.")) return null
        val parts = import.removePrefix("$base.").split(".")
        if (parts.size == 1 || parts[0] == "R") return "app"
        return layerOfPath(parts.joinToString("/"))
    }

    private fun violations(rule: String, offends: (Source, String) -> Boolean): List<String> =
        sources.flatMap { source ->
            source.imports.mapNotNull { import ->
                val to = layerOfImport(import) ?: return@mapNotNull null
                if (to != source.layer && offends(source, to)) "${source.path} imports $import ($rule)" else null
            }
        }

    private fun assertNone(violations: List<String>) =
        assertTrue("\n" + violations.joinToString("\n"), violations.isEmpty())

    @Test
    fun domainIsPureKotlin() {
        // The domain knows nothing about Android, storage, screens or the rest of the app.
        assertNone(violations("domain depends on nothing") { s, _ -> s.layer.startsWith("domain") })
        val android = sources.filter { it.layer.startsWith("domain") }
            .flatMap { s -> s.imports.filter { it.startsWith("android.") || it.startsWith("androidx.") }.map { "${s.path} imports $it" } }
        assertNone(android)
    }

    @Test
    fun coreDoesNotKnowTheFeaturesOrTheData() {
        assertNone(violations("core is shared by everything, so it depends on neither data nor features") { s, to ->
            s.layer.startsWith("core") && (to.startsWith("data") || to.startsWith("feature") || to == "navigation")
        })
    }

    @Test
    fun dataDoesNotKnowTheScreens() {
        assertNone(violations("data is below the screens") { s, to ->
            s.layer.startsWith("data") && (to.startsWith("feature") || to == "navigation" || to.startsWith("core.ui"))
        })
    }

    @Test
    fun dataLayersPointOneWay() {
        // sources (db, prefs, remote) <- sync <- repository: nothing points back up.
        assertNone(violations("local storage and remote must not use sync or the repository") { s, to ->
            (s.layer == "data.local" || s.layer == "data.remote") && (to == "data.sync" || to == "data.repository")
        })
        assertNone(violations("sync must not use the repository") { s, to -> s.layer == "data.sync" && to == "data.repository" })
    }

    @Test
    fun featuresDoNotOpenEachOtherUpExceptWhereAllowed() {
        val allowed = mapOf(
            "feature.home" to setOf("feature.auth", "feature.body", "feature.coach", "feature.plans", "feature.profile"),
            "feature.analytics" to setOf("feature.coach"),
            "feature.history" to setOf("feature.plans"),
            "feature.profile" to setOf("feature.auth"),
        )
        assertNone(violations("a feature may only use the features listed in the architecture notes") { s, to ->
            s.layer.startsWith("feature") && to.startsWith("feature") && to !in allowed[s.layer].orEmpty()
        })
        assertNone(violations("screens never reach back into navigation") { s, to -> s.layer.startsWith("feature") && to == "navigation" })
    }

    @Test
    fun everyPackageMatchesItsFolder() {
        val wrong = sources.mapNotNull { source ->
            val dir = source.path.substringBeforeLast('/', "")
            val expected = if (dir.isEmpty()) base else "$base.${dir.replace('/', '.')}"
            if (source.pkg != expected) "${source.path} declares package ${source.pkg}, expected $expected" else null
        }
        assertNone(wrong)
    }

    @Test
    fun noSourceFileGrowsPastSixHundredLines() {
        assertNone(sources.filter { it.lines > 600 }.map { "${it.path} has ${it.lines} lines: split it by what it shows" })
    }
}

/** The layer of a source path relative to the package root: app, domain, core.ui, core.util, data.local, feature.home, navigation ... */
private fun layerOfPath(path: String): String {
    val parts = path.split("/")
    if (parts.size == 1) return "app" // MainActivity.kt, StrivoApp.kt
    return when (parts[0]) {
        "feature", "data", "core" -> "${parts[0]}.${parts[1].removeSuffix(".kt")}"
        else -> parts[0] // domain, navigation
    }
}
