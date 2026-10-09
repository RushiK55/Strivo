package com.example.strivo.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.strivo.StrivoApp
import com.example.strivo.ui.theme.AppColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val WheelHeight = 160.dp
private val ItemHeight = 45.dp

/** iOS-style number wheel: scrolls with snapping, ticks with haptics and a soft sound. */
@Composable
fun WheelPicker(
    minValue: Int,
    maxValue: Int,
    initialValue: Int,
    onChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    width: Dp = 70.dp,
    /** Keep the label's height free (invisible) so this wheel lines up with a labelled neighbour. */
    reserveLabelSpace: Boolean = false,
) {
    val count = maxValue - minValue + 1
    val initialIndex = (initialValue - minValue).coerceIn(0, count - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val itemPx = with(LocalDensity.current) { ItemHeight.toPx() }

    val currentOnChanged by rememberUpdatedState(onChanged)
    val view = LocalView.current
    val sound = (LocalContext.current.applicationContext as StrivoApp).wheelSound
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState, itemPx) {
        var last = initialIndex
        var soundJob: Job? = null
        snapshotFlow {
            val offsetPastHalf = listState.firstVisibleItemScrollOffset > itemPx / 2
            (listState.firstVisibleItemIndex + if (offsetPastHalf) 1 else 0).coerceIn(0, count - 1)
        }.collect { index ->
            if (index == last) return@collect
            last = index
            // Haptic feedback is instant; the sound is debounced to avoid a "machine gun" effect.
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            soundJob?.cancel()
            soundJob = scope.launch {
                delay(40)
                sound.play()
            }
            currentOnChanged(minValue + index)
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty() || reserveLabelSpace) {
            Text(
                text = label.ifEmpty { " " },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextSecondary,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(12.dp))
        }
        Box(
            modifier = Modifier
                .height(WheelHeight)
                .width(width)
                .clip(RoundedCornerShape(25.dp))
                .background(AppColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            // Selection highlight
            Box(
                modifier = Modifier
                    .height(ItemHeight)
                    .width(width - 12.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(AppColors.Accent.copy(alpha = 0.1f))
                    .border(BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.3f)), RoundedCornerShape(15.dp))
            )
            LazyColumn(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                contentPadding = PaddingValues(vertical = (WheelHeight - ItemHeight) / 2),
                verticalArrangement = Arrangement.Top,
            ) {
                items(count) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ItemHeight)
                            .graphicsLayer { applyWheelEffect(listState, index, itemPx) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (minValue + index).toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = AppColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

/** Two wheels with a dot between them: whole part and a single decimal digit (e.g. 72 . 5). */
@Composable
fun DecimalWheelPicker(
    label: String,
    minValue: Int,
    maxValue: Int,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    intWidth: Dp = 70.dp,
    decimalWidth: Dp = 70.dp,
    dotSize: TextUnit = 30.sp,
) {
    // The wheels keep their own scroll position; only the latest picked digits are tracked here.
    val whole = remember { mutableIntStateOf(value.toInt()) }
    val decimal = remember { mutableIntStateOf(((value - value.toInt()) * 10).roundToInt()) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        WheelPicker(
            label = label,
            minValue = minValue,
            maxValue = maxValue,
            initialValue = whole.intValue,
            onChanged = {
                whole.intValue = it
                onValueChange(it + decimal.intValue / 10.0)
            },
            width = intWidth,
        )
        Text(
            text = ".",
            fontSize = dotSize,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            // Nudge the dot down so it sits level with the wheels' selection rows, not the label above them.
            modifier = Modifier.padding(top = if (label.isNotEmpty()) 14.dp else 0.dp),
        )
        WheelPicker(
            reserveLabelSpace = label.isNotEmpty(),
            minValue = 0,
            maxValue = 9,
            initialValue = decimal.intValue,
            onChanged = {
                decimal.intValue = it
                onValueChange(whole.intValue + it / 10.0)
            },
            width = decimalWidth,
        )
    }
}

/** Fades, shrinks and tilts rows the further they are from the centre of the wheel. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyWheelEffect(
    listState: LazyListState,
    index: Int,
    itemPx: Float,
) {
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val itemCenter = item.offset + item.size / 2f
    // Item offsets are measured from the start of the padded content area.
    val viewportCenter = info.viewportSize.height / 2f - info.beforeContentPadding
    val distance = ((itemCenter - viewportCenter) / itemPx).coerceIn(-2.5f, 2.5f)
    val magnitude = abs(distance)
    alpha = (1f - 0.3f * magnitude).coerceIn(0.2f, 1f)
    scaleX = 1f - 0.08f * magnitude
    scaleY = 1f - 0.08f * magnitude
    rotationX = -distance * 14f
    cameraDistance = 40f * density
}
