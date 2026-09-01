package com.disciplineos.app.ui.enforcer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * Minimal placeholder for the Enforcer's navigation graph.
 * The Enforcer has no onboarding/home/mission-detail screens — those are Console concerns.
 * This exists only so the NavHostFragment in activity_main.xml has a valid start destination.
 * Will be replaced by real Console-initiated screens in Prompt 4.
 */
class EnforcerPlaceholderFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return TextView(requireContext()).apply {
            text = "DisciplineOS Enforcer\n\nEnable the Accessibility Service in Settings to begin."
            setPadding(48, 48, 48, 48)
            textSize = 18f
        }
    }
}
