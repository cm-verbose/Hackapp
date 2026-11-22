package com.example.hackapp.scaffold.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hackapp.citypack.LocalCityPack
import com.example.hackapp.ui.theme.HackappTheme

/**
 * Figma City Packs "LoadingScreen · Montréal" (195:1168): the City Pack logo centred on the Hero
 * background. Shown over the app on open, right after the system splash screen, which shows the
 * same background only.
 */
@Composable
fun CityLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            // Taps must not reach the start screen composing underneath.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .background(HackappTheme.appColors.hero),
        contentAlignment = Alignment.Center,
    ) {
        // Figma draws the 135 px source image in a 260 x 150 dp box; the cropped vector mark at
        // that scale is 204.5 x 132.9 dp, centred like the box.
        Image(
            painter = painterResource(LocalCityPack.current.logo),
            contentDescription = null,
            modifier = Modifier.size(width = 204.5.dp, height = 132.9.dp),
        )
    }
}

@Preview(widthDp = 412, heightDp = 917)
@Composable
private fun CityLoadingScreenPreview() = HackappTheme { CityLoadingScreen() }
