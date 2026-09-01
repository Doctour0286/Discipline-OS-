package com.disciplineos.domain

import com.disciplineos.data.ledger.LedgerDao
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Domain-module architecture boundary test — Enforcer-stripped version.
 * The original test checked that domain code never imports both UnsupervisedSignalDao
 * and LedgerDao (measurement/enforcement boundary, Data Model doc §7, §13.3).
 * Since UnsupervisedSignalDao was moved to web-app-reference/ with the non-enforcement code,
 * this boundary is now enforced by module separation. This test verifies the remaining
 * enforcement-path boundary: domain code may import LedgerDao (for violation recording)
 * but must not import any non-enforcement DAO.
 */
class DomainArchitectureBoundaryTest {

    private val sourceRoot = File("src/main/java")
    private val importLine = Regex("""^\s*import\s+([\w.]+)""")

    @Test
    fun `no domain source file imports deleted non-enforcement DAOs`() {
        check(sourceRoot.exists()) { "Expected source root at ${sourceRoot.absolutePath}" }

        val deletedDaoFqns = listOf(
            "com.disciplineos.data.dao.GoalMissionDao",
            "com.disciplineos.data.dao.MissionPeriodDao",
            "com.disciplineos.data.dao.MissionLogEntryDao",
            "com.disciplineos.data.dao.TriggerDao",
            "com.disciplineos.data.dao.MilestoneDao",
            "com.disciplineos.data.dao.UnsupervisedSignalDao",
            "com.disciplineos.data.dao.AdherenceLedgerDao",
            "com.disciplineos.data.dao.OnboardingEventDao",
            "com.disciplineos.data.dao.PredictiveFailureAlertDismissalDao",
        )

        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val imports = file.readLines()
                    .mapNotNull { importLine.find(it)?.groupValues?.get(1) }
                    .toSet()
                deletedDaoFqns.any { it in imports }
            }
            .map { it.relativeTo(sourceRoot).path }
            .toList()

        assertTrue(
            "Domain files importing deleted non-enforcement DAOs violate the split boundary: $offenders",
            offenders.isEmpty(),
        )
    }
}
