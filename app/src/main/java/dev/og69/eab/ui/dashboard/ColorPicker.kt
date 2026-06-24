package dev.og69.eab.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.*

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    initialSize: Float = 12f,
    onColorSelected: (Color) -> Unit,
    onSizeSelected: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    // Convert initial Color to HSV
    var hsv by remember { mutableStateOf(colorToHsv(initialColor)) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }
    var brushSize by remember { mutableFloatStateOf(initialSize) }

    val currentColor = remember(hsv, alpha) {
        Color.hsv(hsv[0], hsv[1], hsv[2], alpha)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E1E), // Dark theme surface
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(IntrinsicSize.Min),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Choose Color",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Current Color Preview
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Color Wheel
                Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                    ColorWheel(
                        hue = hsv[0],
                        saturation = hsv[1],
                        onColorChanged = { h, s ->
                            hsv = floatArrayOf(h, s, hsv[2])
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Brightness Slider
                Text("Brightness", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = hsv[2],
                    onValueChange = { hsv = floatArrayOf(hsv[0], hsv[1], it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Opacity Slider
                Text("Opacity (See-through)", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = alpha,
                    onValueChange = { alpha = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Brush Size Slider
                Text("Brush Size", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = brushSize,
                    onValueChange = { brushSize = it },
                    valueRange = 2f..48f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Button(
                        onClick = {
                            onColorSelected(currentColor)
                            onSizeSelected(brushSize)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Select", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
    hue: Float,
    saturation: Float,
    onColorChanged: (Float, Float) -> Unit
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val x = change.position.x - size.width / 2
                    val y = change.position.y - size.height / 2
                    val r = sqrt(x * x + y * y)
                    var newHue = (atan2(y, x) * 180 / Math.PI).toFloat()
                    if (newHue < 0) newHue += 360f
                    val newSaturation = (r / (size.width / 2)).coerceIn(0f, 1f)
                    onColorChanged(newHue, newSaturation)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val x = offset.x - size.width / 2
                    val y = offset.y - size.height / 2
                    val r = sqrt(x * x + y * y)
                    var newHue = (atan2(y, x) * 180 / Math.PI).toFloat()
                    if (newHue < 0) newHue += 360f
                    val newSaturation = (r / (size.width / 2)).coerceIn(0f, 1f)
                    onColorChanged(newHue, newSaturation)
                }
            }
    ) {
        // Hue Sweep Gradient
        val sweepGradient = Brush.sweepGradient(
            colors = listOf(
                Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red
            ),
            center = center
        )
        drawCircle(brush = sweepGradient)

        // Saturation Radial Gradient
        val radialGradient = Brush.radialGradient(
            colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
            radius = size.width / 2,
            center = center
        )
        drawCircle(brush = radialGradient)

        // Picker thumb
        val angleRad = hue * Math.PI / 180f
        val thumbR = saturation * (size.width / 2)
        val thumbX = center.x + thumbR * cos(angleRad).toFloat()
        val thumbY = center.y + thumbR * sin(angleRad).toFloat()

        drawCircle(
            color = Color.Black,
            radius = 12.dp.toPx(),
            center = Offset(thumbX, thumbY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = Offset(thumbX, thumbY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}

private fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    val argb = android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv
}


