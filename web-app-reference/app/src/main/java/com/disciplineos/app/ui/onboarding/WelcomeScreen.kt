package com.disciplineos.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings
import com.disciplineos.app.R
import androidx.activity.compose.layoutBounds

/**
 * Entry point shown if Accessibility Service is not yet enabled.
 * Prompts user to enable it via system settings, then navigates to home.
 * If already enabled, navigates directly to home.
 * This replaces the full 9-screen onboarding flow — all other configuration
 * (goals, tiers, profiles, settings) is done via in-app screens from the home dashboard.
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = remember { androidx.compose.ui.LocalContext.current }
    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        var openSettings by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            )
            Text(
                text = stringResource(R.string.welcome_restricts_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            // Check if Accessibility Service is enabled
            if (!isAccessibilityServiceEnabled(context)) {
                Text(
                    text = stringResource(R.string.welcome_enable_sa_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Button(
                    onClick = {
                        openSettings = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.welcome_enable_sa))
                }
            } else {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.welcome_continue))
                }
            }
            if (openSettings) {
                Button(
                    onClick = {
                        context.startActivity(
                            new android.content.Intent(
                                android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
                            )
                        )
                        openSettings = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                ) {
                    Text("Open Settings")
                }
            }
        }
        // After user returns from settings, navigate to home
        LaunchedEffect(openSettings) {
            if (openSettings) {
                onContinue()
            }
        }
    }
}

/** Simple check: verify our Accessibility Service is registered and enabled. */
private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val pm = context.packageManager
    val serviceIntent = new android.content.Intent(
        android.accessibilityservice.AccessibilityServiceInfo.ACTION_VIEW
    )
    serviceIntent.setComponent(
        new android.componentname.ComponentName(
            "com.disciplineos.app",
            "com.disciplineos.app.missionaccessibilityservice.MissionAccessibilityService"
        )
    )
    // Check if the service is enabled in system settings
    val enabled = pm.isEnabled(serviceIntent.getComponent())
    return enabled
}