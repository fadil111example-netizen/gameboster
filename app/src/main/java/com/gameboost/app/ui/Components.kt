package com.gameboost.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .background(
                Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF))),
                shape
            )
            .border(1.dp, Color(0x26FFFFFF), shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val brush = if (enabled) Brush.horizontalGradient(listOf(Violet, Neon))
    else Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF)))
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun MiniGauge(
    title: String,
    value: String,
    progress: Float,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800), label = "gauge")
    GlassCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(
                        color = color.copy(alpha = 0.18f),
                        startAngle = 135f, sweepAngle = 270f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = color,
                        startAngle = 135f, sweepAngle = 270f * anim, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = TextLo, fontSize = 12.sp)
                Text(value, color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BoostHero(ramPercent: Int, boosting: Boolean, onBoost: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "hero")
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "spin",
    )
    val pulse by inf.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pulse",
    )
    val progress by animateFloatAsState(ramPercent / 100f, tween(900), label = "ram")

    Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(230.dp)
                .scale(pulse)
                .background(
                    Brush.radialGradient(listOf(Neon.copy(alpha = 0.30f), Color.Transparent)),
                    CircleShape
                )
        )
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = Color(0x16FFFFFF),
                startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (boosting) {
                rotate(spin) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, Neon, Violet, Color.Transparent)
                        ),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            } else {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Neon, Violet, Pink, Neon)),
                    startAngle = 135f, sweepAngle = 270f * progress, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier
                .size(168.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Violet, Neon)))
                .clickable(enabled = !boosting, onClick = onBoost),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.RocketLaunch, null,
                    tint = Color.White, modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    if (boosting) "Membersihkan…" else "Boost",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                )
                Text("RAM terpakai $ramPercent%", color = Color(0xCCFFFFFF), fontSize = 12.sp)
            }
        }
    }
}
