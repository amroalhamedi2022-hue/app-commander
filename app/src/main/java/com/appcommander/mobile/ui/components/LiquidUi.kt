package com.appcommander.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.appcommander.mobile.ui.theme.LiquidPalette

@Composable
fun LiquidBackground(palette: LiquidPalette, content: @Composable BoxScope.() -> Unit) {
    val infinite = rememberInfiniteTransition(label = "liquid")
    val driftA = infinite.animateFloat(
        initialValue = -0.12f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse),
        label = "blobA",
    ).value
    val driftB = infinite.animateFloat(
        initialValue = 0.15f,
        targetValue = -0.16f,
        animationSpec = infiniteRepeatable(tween(12000), RepeatMode.Reverse),
        label = "blobB",
    ).value

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 0.96f }) {
            drawRect(
                brush = Brush.linearGradient(
                    listOf(
                        palette.accent.copy(alpha = 0.22f),
                        Color.Transparent,
                        palette.second.copy(alpha = 0.13f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(palette.glow.copy(alpha = 0.30f), Color.Transparent)
                ),
                radius = size.minDimension * 0.48f,
                center = Offset(size.width * (0.80f + driftA), size.height * 0.12f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(palette.second.copy(alpha = 0.25f), Color.Transparent)
                ),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * (0.12f + driftB), size.height * 0.62f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.055f),
                radius = size.minDimension * 0.10f,
                center = Offset(size.width * 0.80f, size.height * 0.74f),
            )
            drawCircle(
                color = palette.accent.copy(alpha = 0.10f),
                radius = size.minDimension * 0.055f,
                center = Offset(size.width * 0.15f, size.height * 0.28f),
            )
        }
        content()
    }
}

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f), RoundedCornerShape(26.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(26.dp)),
        content = content,
    )
}

