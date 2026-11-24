package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan

/**
 * Adds high-visibility Android TV D-Pad remote focus highlight, gentle scale lift, and
 * DPAD_CENTER / ENTER activation while remaining 100% responsive to touch on phones & tablets.
 */
fun Modifier.tvFocusableClickable(
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp),
    focusColor: Color = ElectricCyan,
    scaleOnFocus: Float = 1.03f,
    onClick: () -> Unit
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tv_focus_scale"
    )

    this
        .scale(scale)
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (isFocused) {
                Modifier.border(width = 2.5.dp, color = focusColor, shape = shape)
            } else {
                Modifier
            }
        )
        .onPreviewKeyEvent { event ->
            if (enabled && event.type == KeyEventType.KeyUp &&
                (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
            ) {
                onClick()
                true
            } else {
                false
            }
        }
        .focusable(enabled = enabled)
        .clickable(enabled = enabled, onClick = onClick)
}
