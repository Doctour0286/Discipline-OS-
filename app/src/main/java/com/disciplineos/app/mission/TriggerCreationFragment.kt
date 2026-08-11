package com.disciplineos.app.mission

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.disciplineos.app.di.AppContainer
import com.disciplineos.app.ui.mission.TriggerCreationScreen
import com.disciplineos.app.ui.theme.themedComposeView
import com.disciplineos.data.entity.MissionArchetype
import com.disciplineos.data.entity.Trigger
import com.disciplineos.data.entity.TriggerCueType
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Batch G5 (BUILD_PLAN.md), Integration Plan §6. Reached only from
 * [com.disciplineos.app.mission.MissionDetailFragment] (either via the "attach a Trigger?"
 * Hypothesizing-stage prompt, or a direct "add another Trigger" action — base doc §4.3: "no
 * hard cap on triggers per Mission").
 *
 * **Two distinct write paths, matching [com.disciplineos.app.ui.mission.TriggerCreationScreen]'s
 * own kdoc reasoning:**
 * - [TriggerCueType.APP_OPEN] on a [MissionArchetype.CONSTRAINT] mission routes through
 *   [com.disciplineos.domain.usecase.CreateConstraintTriggerUseCase] — the one sanctioned path
 *   that also creates the real `ALWAYS_ON` `MissionPeriod`/`MissionProfile` the blocking
 *   mechanism needs. See that use-case's kdoc for why this must never be a second,
 *   independently-written path.
 * - Every other cue type (including `APP_OPEN` never being offered for a non-Constraint mission
 *   — enforced by the Screen itself not rendering that option, this Fragment's `require` below
 *   is defense-in-depth, same "belt and suspenders" posture `TierSelectionFragment`'s own
 *   `require(tier != Tier.IRON)` uses for an equivalent UI-enforced-but-also-checked
 *   invariant) is a plain [com.disciplineos.data.dao.TriggerDao.insert] with no
 *   `MissionPeriod` involved — matching this project's existing bias against wrapping a
 *   trivial single-table write in use-case ceremony it doesn't need (see
 *   [com.disciplineos.app.home.HomeFragment.recordDismissal]'s identical reasoning).
 */
class TriggerCreationFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        var missionArchetype by mutableStateOf(MissionArchetype.BEHAVIOR_DRIVEN)

        val missionIdArg = arguments?.getString(ARG_MISSION_ID)
        val missionId = missionIdArg?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        if (missionId != null) {
            loadMissionArchetype(missionId) { archetype -> missionArchetype = archetype }
        }

        return themedComposeView {
            TriggerCreationScreen(
                missionArchetype = missionArchetype,
                onCreate = { cueType, cueDescription, responseDescription, packageId ->
                    if (missionId != null) {
                        createTriggerAndFinish(missionId, cueType, cueDescription, responseDescription, packageId)
                    }
                },
                onBack = { findNavController().popBackStack() },
            )
        }
    }

    private fun loadMissionArchetype(missionId: UUID, onLoaded: (MissionArchetype) -> Unit) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)
            val goalMission = database.goalMissionDao().get(missionId) ?: return@launch
            onLoaded(goalMission.archetype)
        }
    }

    private fun createTriggerAndFinish(
        missionId: UUID,
        cueType: TriggerCueType,
        cueDescription: String,
        responseDescription: String,
        packageId: String?,
    ) {
        lifecycleScope.launch {
            val context = requireContext().applicationContext
            val database = AppContainer.database(context)

            if (cueType == TriggerCueType.APP_OPEN) {
                val goalMission = database.goalMissionDao().get(missionId)
                require(goalMission != null && goalMission.archetype == MissionArchetype.CONSTRAINT) {
                    // Screen-enforced (APP_OPEN is only ever offered for a CONSTRAINT mission) —
                    // this is defense-in-depth against a caller reaching this Fragment with an
                    // inconsistent state, not an expected path. See class kdoc.
                    "TriggerCreationFragment: APP_OPEN reached for a non-CONSTRAINT mission " +
                        "($missionId) — should be unreachable via TriggerCreationScreen"
                }
                AppContainer.createConstraintTriggerUseCase(context).execute(
                    missionId = missionId,
                    packageId = requireNotNull(packageId) {
                        "APP_OPEN Trigger creation reached with a null packageId — " +
                            "TriggerCreationScreen's own canCreate gate should have prevented this"
                    },
                    cueDescription = cueDescription,
                )
            } else {
                database.triggerDao().insert(
                    Trigger(
                        id = UUID.randomUUID(),
                        missionId = missionId,
                        cueType = cueType,
                        cueDescription = cueDescription,
                        responseDescription = responseDescription,
                        createdAt = Instant.now(),
                    ),
                )
            }

            findNavController().popBackStack()
        }
    }

    companion object {
        /** Matches `onboarding_nav_graph.xml`'s `triggerCreationFragment` `missionId` argument name. */
        const val ARG_MISSION_ID = "missionId"
    }
}
