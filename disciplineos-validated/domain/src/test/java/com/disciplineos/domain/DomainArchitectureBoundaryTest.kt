package com.disciplineos.domain

import com.disciplineos.data.dao.UnsupervisedSignalDao
import com.disciplineos.data.ledger.LedgerDao
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Domain-module counterpart to `:data`'s `ArchitectureBoundaryTest`. Data Model & Schema doc
 * §7's "measurement never enforces" boundary applies just as much to use-case code as to DAO
 * code — arguably more, since a use-case is exactly where a well-meaning "let's also factor
 * in the unsupervised signal" shortcut would get added. `:data`'s boundary test only scans
 * `:data`'s own `src/main/java` (it's module-relative), so it says nothing about `:domain` —
 * this file exists so the same static check actually covers this module too, rather than the
 * boundary being true here only by inspection.
 *
 * Same coarse-but-auditable approach as the original: scans import lines only, allows kdoc
 * to discuss both types by name (this file does), and is intentionally simple to read over
 * maximally precise. See the `:data` version's kdoc for the full rationale.
 */
class DomainArchitectureBoundaryTest {

    private val sourceRoot = File("src/main/java")
    private val importLine = Regex("""^\s*import\s+([\w.]+)""")

    @Test
    fun `no domain source file imports both UnsupervisedSignalDao and LedgerDao`() {
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
