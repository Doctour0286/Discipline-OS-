package com.disciplineos.data

import com.disciplineos.data.dao.UnsupervisedSignalDao
import com.disciplineos.data.ledger.LedgerDao
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Data Model & Schema doc §7: "CI check ... fail the build if any function reading from
 * UnsupervisedSignal writes to LedgerEntry, DisciplineScore, ReliabilityIndex, or Tier."
 *
 * This is a coarse but effective static check: it scans the `import` lines of every .kt
 * source file in the module and fails if any single file imports BOTH [UnsupervisedSignalDao]
 * and [LedgerDao] (or a TierDao, once one exists). A file that needs both as real
 * dependencies is, by definition, a candidate for crossing the measurement/enforcement
 * boundary — PRD §13.3's "zero write access" language means these two concerns should never
 * be co-located in a class that can see both.
 *
 * Deliberately scans only `import androidx...` / `import com.disciplineos...` style lines,
 * not full file text — kdoc comments are allowed to *discuss* both types (e.g. this file
 * does), which a naive whole-text scan would incorrectly flag. Real dataflow analysis would
 * catch more (e.g. fully-qualified in-line references without an import), but per §7 the
 * goal is a check simple enough to audit by reading it, not maximal precision.
 *
 * If this ever produces a false positive (a legitimate reason to import both — e.g. a
 * top-level DI module wiring both DAOs into *different* classes), split the offending file
 * rather than loosening this check. [DisciplineOsDatabase] and [UnsupervisedDatabase] are
 * already split into separate files for exactly this reason.
 */
class ArchitectureBoundaryTest {

    private val sourceRoot = File("src/main/java")
    private val importLine = Regex("""^\s*import\s+([\w.]+)""")

    @Test
    fun `no source file imports both UnsupervisedSignalDao and LedgerDao`() {
        check(sourceRoot.exists()) { "Expected source root at ${sourceRoot.absolutePath}" }

        val unsupervisedFqn = UnsupervisedSignalDao::class.qualifiedName!!
        val ledgerFqn = LedgerDao::class.qualifiedName!!

        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val imports = file.readLines()
                    .mapNotNull { importLine.find(it)?.groupValues?.get(1) }
                    .toSet()
                unsupervisedFqn in imports && ledgerFqn in imports
            }
            .map { it.relativeTo(sourceRoot).path }
            .toList()

        assertTrue(
            "Files importing both UnsupervisedSignalDao and LedgerDao violate the " +
                "measurement/enforcement boundary (Data Model doc §7, §13.3): $offenders",
            offenders.isEmpty(),
        )
    }
}
