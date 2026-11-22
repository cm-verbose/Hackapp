package com.example.hackapp.scaffold.composables.mainbottombar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import kotlin.reflect.KClass

/** A navigation item for the [MainBottomBar]. */
sealed class NavigationItem(
    val title: String,
    val target: Any,
    val targetClass: KClass<*>
) {
    @Composable
    abstract fun painter(): Painter

    class VectorItem(
        title: String,
        target: Any,
        targetClass: KClass<*>,
        private val icon: ImageVector
    ) : NavigationItem(title, target, targetClass) {
        @Composable
        override fun painter(): Painter = rememberVectorPainter(icon)
    }

    class ResourceItem(
        title: String,
        target: Any,
        targetClass: KClass<*>,
        private val iconRes: Int
    ) : NavigationItem(title, target, targetClass) {
        @Composable
        override fun painter(): Painter = painterResource(iconRes)
    }
}
