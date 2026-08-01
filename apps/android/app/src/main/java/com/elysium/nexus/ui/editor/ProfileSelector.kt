package com.elysium.nexus.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.elysium.nexus.core.profile.Profile

/**
 * The top-of-screen profile selector.
 *
 * `MASTER_ORDER.md` §15 (and §5 "Mapping and Profile
 * Engine") calls for a "selector" of profiles — the
 * user has a library of profiles, and the active
 * profile is the one being edited and the one the
 * engine consumes.
 *
 * Phase 1.3 ships the first slice:
 *
 *  - A horizontally-scrolling row of `AssistChip`s
 *    and `FilterChip`s.
 *  - The current profile is highlighted with
 *    `FilterChip(selected = true)`.
 *  - Tapping a profile calls [onProfileSelected].
 *  - The "New profile" chip (Phase 1.4+) is a future
 *    addition; Phase 1.3 ships the read-only list.
 *
 * ## Why a chip row and not a `DropdownMenu`
 *
 * The chip row is one tap, one visible target. A
 * `DropdownMenu` requires two taps (open, then pick)
 * and obscures the editor. The chip row is the
 * direct-manipulation pattern the §15 spec
 * describes. Phase 1.4+ may add a long-press menu
 * for rename / delete / duplicate.
 */
@Composable
fun ProfileSelector(
    profiles: List<Profile>,
    currentProfileId: Int,
    onProfileSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F12))
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (profiles.isEmpty()) {
            Text(
                text = "(no profiles)",
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(8.dp)
            )
        } else {
            profiles.forEach { profile ->
                val isSelected = profile.id == currentProfileId
                if (isSelected) {
                    FilterChip(
                        selected = true,
                        onClick = { /* already current */ },
                        label = { Text(profile.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF1F6FEB),
                            labelColor = Color.White
                        )
                    )
                } else {
                    AssistChip(
                        onClick = { onProfileSelected(profile.id) },
                        label = { Text(profile.name) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF1A1A1F),
                            labelColor = Color(0xFFF2F2F4)
                        )
                    )
                }
            }
        }
    }
}
