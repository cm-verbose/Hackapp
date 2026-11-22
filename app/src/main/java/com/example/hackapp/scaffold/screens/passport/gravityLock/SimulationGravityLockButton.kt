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
 */
@Composable
fun SimulationGravityLockButton(
    isLocked: Boolean,
    onLockedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = { onLockedChange(!isLocked) },
        modifier = modifier,
    ) {
        if (isLocked) {
            Icon(Icons.Default.Lock, contentDescription = "Unlock gravity")
        } else {
            Icon(Icons.Default.LockOpen, contentDescription = "Lock gravity")
        }
    }
}
