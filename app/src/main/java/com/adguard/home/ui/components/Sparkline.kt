package com.adguard.home.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun Sparkline(
    points: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    timeUnit: String = "hours"
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var selectedOffset by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { offset ->
                        if (points.isNotEmpty()) {
                            val step = size.width / (points.size - 1).coerceAtLeast(1)
                            val idx = (offset.x / step).roundToInt().coerceIn(0, points.size - 1)
                            selectedIndex = idx
                            selectedOffset = offset
                            tryAwaitRelease()
                            selectedIndex = null
                            selectedOffset = null
                        }
                    }
                )
            }
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (points.isNotEmpty()) {
                            val step = size.width / (points.size - 1).coerceAtLeast(1)
                            val idx = (offset.x / step).roundToInt().coerceIn(0, points.size - 1)
                            selectedIndex = idx
                            selectedOffset = offset
                        }
                    },
                    onDragEnd = {
                        selectedIndex = null
                        selectedOffset = null
                    },
                    onDragCancel = {
                        selectedIndex = null
                        selectedOffset = null
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (points.isNotEmpty()) {
                            val step = size.width / (points.size - 1).coerceAtLeast(1)
                            val idx = (change.position.x / step).roundToInt().coerceIn(0, points.size - 1)
                            selectedIndex = idx
                            selectedOffset = change.position
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val paddingBottom = 4.dp.toPx()
            val usableHeight = height - paddingBottom

            if (points.isEmpty() || points.all { it == 0f }) {
                // Flat baseline
                drawLine(
                    color = lineColor.copy(alpha = 0.4f),
                    start = Offset(0f, usableHeight),
                    end = Offset(width, usableHeight),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                return@Canvas
            }

            val maxVal = points.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val minVal = 0f
            val range = (maxVal - minVal).coerceAtLeast(1f)
            val stepX = width / (points.size - 1).coerceAtLeast(1)

            val linePath = Path()
            val fillPath = Path()

            points.forEachIndexed { index, value ->
                val x = index * stepX
                val normalizedY = 1f - ((value - minVal) / range)
                val y = (normalizedY * (usableHeight - 6.dp.toPx())) + 3.dp.toPx()

                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }

                if (index == points.size - 1) {
                    fillPath.lineTo(x, height)
                    fillPath.close()
                }
            }

            // Draw gradient fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.25f),
                        lineColor.copy(alpha = 0.02f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw sparkline stroke
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw interactive selection indicator
            selectedIndex?.let { idx ->
                val x = idx * stepX
                val value = points[idx]
                val normalizedY = 1f - ((value - minVal) / range)
                val y = (normalizedY * (usableHeight - 6.dp.toPx())) + 3.dp.toPx()

                // Vertical guideline
                drawLine(
                    color = lineColor.copy(alpha = 0.5f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1.dp.toPx()
                )

                // Highlight dot
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }

        // Floating tooltip on touch
        selectedIndex?.let { idx ->
            val value = points[idx].roundToInt()
            val totalPoints = points.size
            val timeAgo = (totalPoints - 1 - idx)
            val timeText = if (timeAgo == 0) "now" else "$timeAgo $timeUnit ago"

            Surface(
                modifier = Modifier
                    .offset {
                        val posX = (selectedOffset?.x ?: 0f).roundToInt() - 40.dp.roundToPx()
                        IntOffset(posX.coerceIn(0, 500), -32.dp.roundToPx())
                    },
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 4.dp
            ) {
                Text(
                    text = "$value ($timeText)",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
