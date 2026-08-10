package com.disciplineos.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.disciplineos.app.R
import com.disciplineos.app.applist.InstalledAppsProvider.InstalledApp

/**
 * Onboarding, Consent & Interaction Spec §2.8 (Mission Profile Setup) — app picker replacing
 * the original free-text "type one package id per line" fields. See
 * [MissionProfileSetupScreen]'s kdoc and `ROADMAP.md`'s matching entry for why: a blocklist a
 * user can mistype into silently doing nothing is a correctness risk in the one mechanism this
 * whole product is built around (mission enforcement), not just a polish item.
 *
 * Presentation only, same split every other onboarding screen in this package already follows
 * — [apps] is supplied by the hosting Fragment (via [com.disciplineos.app.applist
 * .InstalledAppsProvider], which does the actual [android.content.pm.PackageManager] query off
 * this composable). [selectedPackages] is the current selection (by package name, matching
 * [com.disciplineos.data.entity.MissionProfile.allowlist]/`blocklist`'s existing
 * `List<String>` shape exactly — no schema change needed for this picker to slot in).
 *
 * A simple search filter is included since a real device can have 100+ launchable apps —
 * scrolling an unfiltered list to find one specific app would reintroduce a different kind of
 * friction than the one this screen is fixing.
 */
@Composable
fun AppPickerScreen(
    title: String,
    apps: List<InstalledApp>,
    selectedPackages: Set<String>,
    onToggle: (packageName: String) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(
                    R.string.app_picker_selected_count,
                    selectedPackages.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.app_picker_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            if (apps.isEmpty()) {
                Text(
                    text = stringResource(R.string.app_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = selectedPackages.contains(app.packageName),
                            onToggle = { onToggle(app.packageName) },
                        )
                    }
                }
            }

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
            ) {
                Text(stringResource(R.string.app_picker_done))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_placeholder_back))
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val bitmap = remember(app.packageName) { app.icon?.toBitmapOrNull() }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp),
                    ),
            )
        }

        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

/**
 * [android.graphics.drawable.Drawable] has no zero-dependency direct path to Compose's
 * [androidx.compose.ui.graphics.ImageBitmap] — this converts via an intermediate
 * [android.graphics.Bitmap] canvas draw. No image-loading library (Coil/Glide) is added for
 * this alone; app icons are already in-memory [Drawable]s from [android.content.pm
 * .PackageManager], not remote/URL images, so none of what those libraries are for (network
 * fetch, disk caching, request de-duplication) applies here.
 */
private fun android.graphics.drawable.Drawable.toBitmapOrNull():
    androidx.compose.ui.graphics.ImageBitmap? {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    return try {
        val bitmap = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: OutOfMemoryError) {
        null
    }
}
