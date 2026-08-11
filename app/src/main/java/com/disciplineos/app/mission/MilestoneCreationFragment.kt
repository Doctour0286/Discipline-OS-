package com.disciplineos.app.mission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.di.AppContainer
import com.disciplineos.app.ui.mission.MilestoneCreationScreen
import com.disciplineos.app.ui.theme.themedComposeView
import com.disciplineos.data.entity.Milestone
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Batch G6 (BUILD_PLAN.md), Integration Plan §7, base design doc Addendum §B.2. Reached only
 * from [com.disciplineos.app.mission.MissionDetailFragment]'s "Add a milestone" action — same
 * one-directional, no-re-entry-guard shape [com.disciplineos.app.mission.TriggerCreationFragment]
 * already established for its own "Add a Trigger" destination (Addendum §B.2 states no cap on
 * milestone count either, matching base doc §4.3's identical "no hard cap on triggers" framing
 * for that entity).
 *
 * **A plain [com.disciplineos.data.dao.MilestoneDao.insert], no use-case involved** — same
 * "trivial single-table write doesn't need use-case ceremony" posture
 * [TriggerCreationFragment]'s own kdoc states for its own non-`APP_OPEN` path (citing
 * [com.disciplineos.app.home.HomeFragment.recordDismissal]'s identical reasoning). Unlike
 * `APP_OPEN` Trigger creation, there is no second table/transaction this write needs to stay in
 * lockstep with — a [Milestone] row stands alone.
 */
class MilestoneCreationFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val missionIdArg = arguments?.getString(ARG_MISSION_ID)
        val missionId = missionIdArg?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        return themedComposeView {
            MilestoneCreationScreen(
                onCreate = { label, targetValue ->
                    if (missionId != null) {
                        createMilestoneAndFinish(missionId, label, targetValue)
                    }
                },
                onBack = { findNavController().popBackStack() },
            )
        }
    }

    private fun createMilestoneAndFinish(missionId: UUID, label: String, targetValue: Double?) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)

            database.milestoneDao().insert(
                Milestone(
                    id = UUID.randomUUID(),
                    missionId = missionId,
                    label = label,
                    targetValue = targetValue,
                    // No date-picker UI wired this pass — see MilestoneCreationScreen's own
                    // kdoc for why targetDate is always null as of this pass.
                    targetDate = null,
                    achievedAt = null,
                ),
            )

            findNavController().popBackStack()
        }
    }

    companion object {
        /** Matches `onboarding_nav_graph.xml`'s `milestoneCreationFragment` `missionId` argument name. */
        const val ARG_MISSION_ID = "missionId"
    }
}
