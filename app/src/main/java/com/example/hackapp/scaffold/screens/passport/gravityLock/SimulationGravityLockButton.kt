package com.example.hackapp.scaffold.screens.passport.gravityLock

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Toggles whether device-tilt gravity applies to the physics sandbox.
 *
 * @param isLocked `true` when gravity is locked (not applied to the simulation).
 * @param onLockedChange Called with the new locked state when the button is pressed.
 * @param lockedDescription Spoken action while locked.
 * @param unlockedDescription Spoken action while unlocked.
 */
@Composable
fun SimulationGravityLockButton(
    isLocked: Boolean,
    onLockedChange: (Boolean) -> Unit,
    lockedDescription: String?,
    unlockedDescription: String?,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = { onLockedChange(!isLocked) },
        modifier = modifier,
    ) {
        if (isLocked) {
            Icon(Icons.Default.Lock, contentDescription = lockedDescription)
        } else {
            Icon(Icons.Default.LockOpen, contentDescription = unlockedDescription)
        }
    }
}
