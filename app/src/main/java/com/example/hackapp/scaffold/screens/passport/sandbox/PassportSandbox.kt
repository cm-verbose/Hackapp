package com.example.hackapp.scaffold.screens.passport.sandbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hackapp.R

/**
 * Default set of composable items for the physics sandbox.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun defaultSandboxItems(): List<SandboxItem> {
    return listOf(
        SandboxItem(
            shape = MaterialShapes.VerySunny.toShape(),
            width = 180.dp,
            height = 180.dp,
            color = Color(0xFF1E88E5),
            content = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_passport),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Passport",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        ),
        SandboxItem(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            width = 160.dp,
            height = 160.dp,
            color = Color(0xFF43A047),
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Visa",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        ),
        SandboxItem(
            shape = MaterialShapes.Cookie7Sided.toShape(),
            width = 100.dp,
            height = 100.dp,
            color = Color(0xFFE53935),
            content = {
                Icon(
                    imageVector = Icons.Default.FlightTakeoff,
                    contentDescription = "Flight",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        ),
        SandboxItem(
            shape = RoundedCornerShape(24.dp),
            width = 150.dp,
            height = 80.dp,
            color = Color(0xFFFB8C00),
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hotel,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hotel",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        ),
        SandboxItem(
            shape = CircleShape,
            width = 110.dp,
            height = 110.dp,
            color = Color(0xFF8E24AA),
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Stamp",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        ),
        SandboxItem(
            shape = RoundedCornerShape(16.dp),
            width = 160.dp,
            height = 80.dp,
            color = Color(0xFF00ACC1),
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ConfirmationNumber,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ticket",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    )
}

/**
 * Passport sandbox element where composables can float around and be rotated
 * based on the user's screen orientation.
 *
 * @param modifier The modifier to be applied to the sandbox layout.
 * @param items List of [SandboxItem]s containing composable UI content and physical parameters.
 */
@Composable
fun PassportSandbox(
    modifier: Modifier = Modifier,
    items: List<SandboxItem> = defaultSandboxItems(),
) {
    PhysicsScreen(
        modifier = modifier,
        items = items,
    )
}
