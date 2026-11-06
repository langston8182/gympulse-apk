package com.gympulse.app.core.designsystem.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Minimal drag-and-drop reordering for a LazyColumn. The dragged item is tracked by key, and
 * [onMove] receives lazy-list indices each time it crosses the middle of a neighbour.
 */
class ReorderableListState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMoveState: () -> (Int, Int) -> Unit,
    private val canMoveOverState: () -> (Any) -> Boolean,
    private val onDragFinished: () -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    private var initialOffset = 0
    private var dragDelta by mutableFloatStateOf(0f)

    fun isDragging(key: Any): Boolean = draggingKey == key

    fun offsetOf(key: Any): Float {
        if (key != draggingKey) return 0f
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        return initialOffset + dragDelta - item.offset
    }

    fun start(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        initialOffset = item.offset
        dragDelta = 0f
    }

    fun drag(delta: Float) {
        val key = draggingKey ?: return
        dragDelta += delta
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val start = initialOffset + dragDelta
        val middle = start + item.size / 2f
        val target = info.visibleItemsInfo.firstOrNull { other ->
            other.key != key && middle >= other.offset && middle <= other.offset + other.size && canMoveOverState()(other.key)
        }
        if (target != null) {
            val keepFirst = listState.firstVisibleItemIndex
            val keepOffset = listState.firstVisibleItemScrollOffset
            onMoveState()(item.index, target.index)
            if (item.index == keepFirst || target.index == keepFirst) {
                scope.launch { listState.scrollToItem(keepFirst, keepOffset) }
            }
        }
        val end = start + item.size
        val overscroll = when {
            end > info.viewportEndOffset -> end - info.viewportEndOffset
            start < info.viewportStartOffset -> start - info.viewportStartOffset
            else -> 0f
        }
        if (overscroll != 0f) scope.launch { listState.scrollBy(overscroll * 0.25f) }
    }

    fun end() {
        if (draggingKey != null) onDragFinished()
        draggingKey = null
        dragDelta = 0f
    }
}

@Composable
fun rememberReorderableListState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    canMoveOver: (key: Any) -> Boolean = { true },
    onDragFinished: () -> Unit = {},
): ReorderableListState {
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentCanMove by rememberUpdatedState(canMoveOver)
    val currentFinished by rememberUpdatedState(onDragFinished)
    return remember(listState) {
        ReorderableListState(listState, scope, { currentOnMove }, { currentCanMove }, { currentFinished() })
    }
}

/** Applies the drag translation to the item being dragged. */
fun Modifier.reorderableItem(state: ReorderableListState, key: Any): Modifier =
    this
        .zIndex(if (state.isDragging(key)) 1f else 0f)
        .graphicsLayer {
            translationY = state.offsetOf(key)
            val dragging = state.isDragging(key)
            scaleX = if (dragging) 1.02f else 1f
            scaleY = if (dragging) 1.02f else 1f
            shadowElevation = if (dragging) 16f else 0f
        }

/** Drag handle: dragging starts immediately. */
@Composable
fun Modifier.dragHandle(state: ReorderableListState, key: Any): Modifier {
    val haptics = rememberEvoHaptics()
    return pointerInput(key) {
        detectDragGestures(
            onDragStart = {
                haptics.longPress()
                state.start(key)
            },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        )
    }
}

/** Whole item draggable after a long press. */
@Composable
fun Modifier.longPressDraggable(state: ReorderableListState, key: Any): Modifier {
    val haptics = rememberEvoHaptics()
    return pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                haptics.longPress()
                state.start(key)
            },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        )
    }
}
