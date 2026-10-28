package dev.alializadeh.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A fast-scroll thumb along the end edge of a [listState]'s list (People sorted by Name). Dragging
 * it jumps through the list and shows the section's [labelAt] (the letter) in a bubble beside it.
 * Hidden while the list fits; TalkBack users scroll the list itself, so it's hidden from them.
 */
@Composable
fun FastScrollThumb(
    listState: LazyListState,
    labelAt: (itemIndex: Int) -> String?,
    modifier: Modifier = Modifier,
) {
    val canScroll by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    if (!canScroll) return
    val scope = rememberCoroutineScope()
    val currentLabelAt by rememberUpdatedState(labelAt)
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    // The touch strip is TouchWidth wide at the end; the bubble lays out beside it.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .semantics { hideFromAccessibility() },
    ) {
        val trackHeight = constraints.maxHeight.toFloat()
        val thumbHeight = with(density) { ThumbHeight.toPx() }
        val travel = (trackHeight - thumbHeight).coerceAtLeast(1f)
        val fraction by remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val visible = info.visibleItemsInfo.size
                val scrollable = (info.totalItemsCount - visible).coerceAtLeast(1)
                (listState.firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
            }
        }
        fun scrollTo(y: Float) {
            val info = listState.layoutInfo
            val scrollable = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(0)
            val target = (((y - thumbHeight / 2) / travel).coerceIn(0f, 1f) * scrollable).roundToInt()
            scope.launch { listState.scrollToItem(target) }
        }
        val thumbY = (fraction * travel).roundToInt()
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(TouchWidth)
                .pointerInput(listState, travel) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            scrollTo(offset.y)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            scrollTo(change.position.y)
                        },
                    )
                },
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbY) }
                .padding(end = 4.dp)
                .size(width = if (dragging) 8.dp else 6.dp, height = ThumbHeight)
                .clip(RoundedCornerShape(50))
                .background(if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        )
        // Recomposes only when the bubble's letter changes, not on every scrolled item.
        val bubble by remember(listState) {
            derivedStateOf { if (dragging) currentLabelAt(listState.firstVisibleItemIndex) else null }
        }
        val label = bubble
        if (label != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 50),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-with(density) { BubbleGap.roundToPx() }, thumbY + (thumbHeight / 2).roundToInt() - with(density) { BubbleSize.roundToPx() }) }
                    .size(BubbleSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

private val TouchWidth = 28.dp
private val ThumbHeight = 48.dp
private val BubbleSize = 56.dp
private val BubbleGap = 36.dp
