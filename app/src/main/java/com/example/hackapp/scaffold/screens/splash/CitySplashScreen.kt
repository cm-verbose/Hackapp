package com.example.hackapp.scaffold.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.R
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.ui.components.HackappIcon
import com.example.hackapp.ui.components.HackappIconView
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma City Packs "SplashScreen · Montréal" (167:598): the City Pack logo and the "Plan Montréal ★"
 * branding on the Hero background. Shown over the app right after the system splash screen, which
 * shows the same background only. Always uses the dark scheme.
 */
@Composable
fun CitySplashScreen(modifier: Modifier = Modifier) {
    HackappTheme(darkTheme = true) {
        val scheme = MaterialTheme.colorScheme
        Box(
            modifier
                .fillMaxSize()
                // Taps must not reach the start screen composing underneath.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .background(HackappTheme.appColors.hero),
        ) {
            // Figma SplashIcon: the mark inside the 160dp visible circle of a 240dp splash icon.
            Image(
                painter = painterResource(LocalCityPack.current.logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 130.dp, height = 84.5.dp),
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp)
                    .background(scheme.primary, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary)
                HackappIconView(HackappIcon.Star, size = 22.dp, tint = scheme.onPrimary)
            }
        }
    }
}

@Preview(widthDp = 412, heightDp = 917)
@Composable
private fun CitySplashScreenPreview() {
    CitySplashScreen()
}
