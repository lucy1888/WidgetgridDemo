package com.wwwcg.kuikly.widgetgrid.demo

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.Scale
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.EventName
import com.tencent.kuikly.core.base.event.PanGestureParams
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.CallbackRef
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.max

data class GridPosition(
    val x: Float,
    val y: Float,
    val row: Int,
    val col: Int,
)

class WidgetGridAttr : ComposeAttr() {
    var config: WidgetGridConfig = WidgetGridConfig()
    var editing: Boolean by observable(false)
    var gridWidth: Float = 0f
    internal var _cardContentBuilder: (ViewContainer<*, *>.(WidgetGridItemData) -> Unit)? = null

    fun cardContent(builder: ViewContainer<*, *>.(WidgetGridItemData) -> Unit) {
        _cardContentBuilder = builder
    }
}

class WidgetGridEvent : ComposeEvent() {
    private var _onEditingChanged: ((Boolean) -> Unit)? = null
    private var _onReorder: ((fromIndex: Int, toIndex: Int) -> Unit)? = null
    private var _onDelete: ((WidgetGridItemData) -> Unit)? = null
    private var _onDragStateChanged: ((Boolean) -> Unit)? = null
    fun onDragStateChanged(handler: (Boolean) -> Unit) { _onDragStateChanged = handler }
    internal fun fireDragStateChanged(isDragging: Boolean) { _onDragStateChanged?.invoke(isDragging) }

    fun onEditingChanged(handler: (Boolean) -> Unit) { _onEditingChanged = handler }
    fun onReorder(handler: (fromIndex: Int, toIndex: Int) -> Unit) { _onReorder = handler }
    fun onDelete(handler: (WidgetGridItemData) -> Unit) { _onDelete = handler }

    internal fun fireEditingChanged(editing: Boolean) { _onEditingChanged?.invoke(editing) }
    internal fun fireReorder(from: Int, to: Int) { _onReorder?.invoke(from, to) }
    internal fun fireDelete(item: WidgetGridItemData) { _onDelete?.invoke(item) }
}

class WidgetGridView : ComposeView<WidgetGridAttr, WidgetGridEvent>() {

    companion object {
        private const val TAG = "WidgetGrid"
    }

    internal var cardList by observableList<WidgetGridItemData>()
    internal var visualOrder by observable<List<Int>>(emptyList())

    private var isDragging = false
    private var dragCardData: WidgetGridItemData? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var lastTargetVisualIndex = -1
    // 长按相关
    private var longPressTimer: CallbackRef? = null
    private val longPressDelay = 500 // ms
    private val longPressMoveThreshold = 10f // px

    // ❌ 删除了全局 pos 变量: private var pos by observable(...)

    private var shakeTimerRef: CallbackRef? = null
    private var resumeShakeTimerRef: CallbackRef? = null
    private var shakeDirection = 1
    private var dragStartLogicalPos: GridPosition = GridPosition(0f, 0f, 0, 0)
    private var dragStartVisualIndex: Int = -1
    private var lastEditingState = false

    internal fun recalculateAllPositions() {
        var currentRow = 0
        var currentCol = 0

        visualOrder.forEachIndexed { visualIndex, originalIndex ->
            if (originalIndex >= cardList.size) return@forEachIndexed
            val card = cardList[originalIndex]
            val spanX = card.spanX

            // 1. 换行逻辑判断
            if (currentCol + spanX > config.columnCount) {
                currentRow++
                currentCol = 0
            }

            // 2. 计算当前卡片的网格坐标
            val row = currentRow
            val col = currentCol

            // 3. 计算像素坐标
            val x = col * (getCardWidth() + config.cardSpacing)
            val y = row * (config.cardHeight + config.cardSpacing)

            val newPos = GridPosition(x, y, row, col)

            // 4. 更新卡片状态
            // 只有当位置真正变化时才标记，减少不必要的重绘触发（虽然 observable 会自动处理，但日志更清晰）
            if (card.pos != newPos) {
                KLog.d(TAG, "Recalc Pos: Card(${card.id}) [VisIdx=$visualIndex] -> Row=$row, Col=$col, Pos=($x, $y)")
                card.pos = newPos
            }

            // 【关键】无论 pos 是否变化，只要执行了全量重算，必须清零 offset
            // 防止之前拖拽或动画残留的 offset 导致位置偏移
            if (card.offsetX != 0f || card.offsetY != 0f) {
                card.offsetX = 0f
                card.offsetY = 0f
            }

            // 更新 layoutVersion 触发 UI 刷新 (如果 pos 变了，observable 会触发；如果只清了 offset，需要手动触发或依赖 offset 的 observable)
            // 由于 offsetX/Y 也是 observable 的，上面赋值会自动触发 UI 更新，无需手动调 layoutVersion
            // 但为了保险起见，如果 pos 没变但 offset 清了，也可以显式触发一次（可选）
            if (card.pos == newPos && (card.offsetX != 0f || card.offsetY != 0f)) {
                // offset 变化本身是 observable 的，通常不需要额外操作
            }

            // 5. 移动光标到下一格
            currentCol += spanX
            if (currentCol >= config.columnCount) {
                currentRow++
                currentCol = 0
            }
        }
    }

    // ==================== 公开 API 简化 ====================

    fun addItem(item: WidgetGridItemData) {
        val originalIndex = cardList.size
        cardList.add(item)

        // 更新视觉顺序：新卡片默认追加到末尾
        val newOrder = if (visualOrder.isEmpty()) listOf(originalIndex) else visualOrder + originalIndex
        visualOrder = newOrder

        // 【优化】一键重算所有位置
        recalculateAllPositions()

        KLog.d(TAG, "addItem: id=${item.id}, total=${cardList.size}")
    }

    fun addItems(items: List<WidgetGridItemData>) {
        if (items.isEmpty()) return
        val startOriginalIndex = cardList.size
        cardList.addAll(items)

        // 生成新卡片的原始索引列表
        val newOriginalIndices = (startOriginalIndex until cardList.size).toList()

        // 更新视觉顺序
        val newOrder = if (visualOrder.isEmpty()) newOriginalIndices else visualOrder + newOriginalIndices
        visualOrder = newOrder

        // 【优化】一键重算所有位置
        recalculateAllPositions()

        KLog.d(TAG, "addItems: count=${items.size}, total=${cardList.size}")
    }

    fun removeItem(id: Int) {
        val item = cardList.find { it.id == id } ?: return
        val originalIndex = cardList.indexOf(item)

        // 1. 先从数据源移除 (注意：这会导致后面元素的索引前移)
        cardList.remove(item)

        // 2. 更新视觉顺序：
        // A. 首先移除被删除的那个索引
        val newVisualOrder = visualOrder.toMutableList().apply {
            removeAll { it == originalIndex }
        }

        // B. 【关键修复】修正剩余索引：所有大于 originalIndex 的索引都需要减 1
        // 因为 cardList 中该位置之后的元素都向前挪了一位
        val correctedVisualOrder = newVisualOrder.map { idx ->
            if (idx > originalIndex) idx - 1 else idx
        }

        // 3. 应用修正后的视觉顺序
        visualOrder = correctedVisualOrder

        // 5. 重算位置
        recalculateAllPositions()

        // 6. 处理动画状态
        stopShakeAnimation()
        if (lastEditingState && config.shakeEnabled) {
            setTimeout(300) { startShakeAnimation() }
        }

        KLog.d(TAG, "removeItem: id=$id, remaining=${cardList.size}, visualOrder=$visualOrder")
    }

    fun getItems(): List<WidgetGridItemData> {
        return visualOrder.filter { it < cardList.size } // 先过滤掉无效索引
            .map { cardList[it] }                        // 再安全获取
    }

    fun setEditing(editing: Boolean) {
        setEditingInternal(editing)
        event.fireEditingChanged(editing)
    }

    private val config: WidgetGridConfig get() = attr.config

    private fun getCardWidth(): Float {
        return (attr.gridWidth - config.cardSpacing * (config.columnCount - 1)) / config.columnCount
    }

    private fun getItemWidth(item: WidgetGridItemData): Float {
        return if (item.spanX == 2) getCardWidth() * 2 + config.cardSpacing else getCardWidth()
    }
    private fun updateAllPositionsBase() {
        cardList.forEach { card ->
            val originalIdx = cardList.indexOf(card)
            val visIdx = visualOrder.indexOf(originalIdx)
            if (visIdx == -1) return@forEach

            val newPos = calculatePositionByVisualIndex(visualOrder, visIdx)

            // 只有 pos 变化才更新，避免不必要的 observable 通知，但这里必须更新以反映新布局
            if (card.pos != newPos) {
                card.pos = newPos
                card.layoutVersion++
            }
            // 注意：这里不再执行 card.offsetX = 0f !!!
        }
    }
    // ✅ 核心计算函数：只负责计算，不负责副作用
    private fun calculatePositionByVisualIndex(order: List<Int>, visualIndex: Int): GridPosition {
        if (visualIndex < 0 || visualIndex >= order.size) return GridPosition(0f, 0f, 0, 0)
        var currentRow = 0
        var currentCol = 0
        for (i in 0 until visualIndex) {
            val originalIdx = order[i]
            if (originalIdx >= cardList.size) continue
            val card = cardList[originalIdx]
            if (currentCol + card.spanX > config.columnCount) { currentRow++; currentCol = 0 }
            currentCol += card.spanX
            if (currentCol >= config.columnCount) { currentRow++; currentCol = 0 }
        }
        if (visualIndex < order.size) {
            val originalIdx = order[visualIndex]
            if (originalIdx < cardList.size) {
                val card = cardList[originalIdx]
                if (currentCol + card.spanX > config.columnCount) { currentRow++; currentCol = 0 }
            }
        }
        return GridPosition(
            currentCol * (getCardWidth() + config.cardSpacing),
            currentRow * (config.cardHeight + config.cardSpacing),
            currentRow, currentCol
        )
    }

    // ✅ 新增：批量更新所有卡片的位置到它们各自的 pos 属性中
    // ✅ 修正版：批量更新所有卡片的位置，并强制清零 offset
    private fun updateAllPositions() {
        cardList.forEach { card ->
            val originalIdx = cardList.indexOf(card)
            val visIdx = visualOrder.indexOf(originalIdx)

            if (visIdx == -1) return@forEach

            // 1. 计算新的逻辑位置 (使用您代码中现有的函数名)
            val newPos = calculatePositionByVisualIndex(visualOrder, visIdx)

            // 2. 只有当位置确实发生变化时才更新 (避免不必要的重绘)
            if (card.pos != newPos) {
                KLog.d(TAG, "updateAllPositions: Card(${card.id}) oldPos=${card.pos}, newPos=$newPos, visualOrder=$visualOrder")

                // --- 关键修复开始 ---

                // A. 更新逻辑坐标
                card.pos = newPos

                // B. 【核心修复】立即清零偏移量！
                // 之前的问题就在这里：pos 变了，但 offset 还保留着巨大的数值，
                // 导致 最终渲染位置(pos + offset) 远超父容器高度。
                card.offsetX = 0f
                card.offsetY = 0f

                // C. 标记布局版本变化，触发 UI 刷新
                card.layoutVersion++

                // --- 关键修复结束 ---
            } else {
                // 即使 pos 没变，如果是拖拽结束后的重置，也要确保 offset 是 0
                // 防止某些边缘情况下 offset 残留
                if (card.offsetX != 0f || card.offsetY != 0f) {
                    card.offsetX = 0f
                    card.offsetY = 0f
                    card.layoutVersion++
                }
            }
        }
    }

    private fun calculateTotalRows(): Int {
        if (visualOrder.isEmpty()) return 1
        val lastVisIdx = visualOrder.size - 1
        val lastPos = calculatePositionByVisualIndex(visualOrder, lastVisIdx)
        return lastPos.row + 1
    }

    private fun handleDrag(params: PanGestureParams, cardData: WidgetGridItemData, currentVisualIndexFromVfor: Int) {
        val originalIndex = cardList.indexOf(cardData)
        val realCurrentVisualIndex = visualOrder.indexOf(originalIndex)
        if (realCurrentVisualIndex == -1) return

        when (params.state) {
            "start" -> startDragging(params, cardData, realCurrentVisualIndex)
            "move" -> {
                if (!isDragging) startDragging(params, cardData, realCurrentVisualIndex)
                if (dragCardData != cardData) return

                val deltaX = params.pageX - dragStartX
                val deltaY = params.pageY - dragStartY
                cardData.offsetX = deltaX
                cardData.offsetY = deltaY

                val targetVisualIndex = findTargetVisualIndex(cardData, realCurrentVisualIndex, deltaX, deltaY)
                if (targetVisualIndex != lastTargetVisualIndex) {
                    lastTargetVisualIndex = targetVisualIndex
                    previewReorder(realCurrentVisualIndex, targetVisualIndex, cardData)
                }
            }
            "end" -> {
                if (!isDragging || dragCardData != cardData) { resetDragState(); return }

                val targetVisualIndex = lastTargetVisualIndex
                val startVisIdx = dragStartVisualIndex

                if (startVisIdx < 0 || startVisIdx >= visualOrder.size) {
                    resetDragState()
                    return
                }

                // 1. 停止抖动，避免干扰重排动画
                stopShakeAnimation()

                // 2. 【关键优化】在修改数据前，记录所有卡片当前的“绝对视觉位置”
                // 这是动画的起点。必须包含 pos + 当前的 offset (拖拽中的偏移)
                val oldVisualPositions = cardList.associateWith { card ->
                    GridPosition(
                        x = card.pos.x + card.offsetX,
                        y = card.pos.y + card.offsetY,
                        row = card.pos.row, // row/col 暂时不重要，主要是 x/y
                        col = card.pos.col
                    )
                }

                cardList.forEach { c ->
                    c.isDragging = false
                    c.shakeAngle = 0f
                    c.shakeKey++
                }

                if (targetVisualIndex != startVisIdx && targetVisualIndex in 0 until visualOrder.size) {
                    // 3. 更新视觉顺序 (数据模型变更)
                    val newOrder = visualOrder.toMutableList()
                    val movedOriginalIndex = newOrder.removeAt(startVisIdx)
                    newOrder.add(targetVisualIndex, movedOriginalIndex)
                    visualOrder = newOrder

                    // 4. 重新计算所有卡片的基准位置 (pos 变为终点)
                    // 注意：此时 offset 还是旧的 (拖拽的 deltaX/Y)，或者如果是其他卡片则是预览的 offset
                    // 我们需要立即重置所有卡片的 offset 为 0 吗？不，我们要利用它们做动画。
                    // 策略：更新 pos -> 计算 (旧视觉 - 新 pos) -> 设为新 offset -> 归零 offset

                    updateAllPositionsBase() // 只更新 pos，不强制清零 offset (我们需要手动控制)

                    // 5. 应用平滑过渡动画
                    applySmoothTransition(dragCardData!!, oldVisualPositions)

                    // 6. 触发事件
                    event.fireReorder(startVisIdx, targetVisualIndex)

                    // 7. 延迟恢复抖动
                    resumeShakeTimerRef = setTimeout((config.dragAnimationDuration * 1000).toInt() + 100) {
                        if (lastEditingState && config.shakeEnabled) startShakeAnimation()
                    }
                } else {
                    // 未移动：复位拖拽卡片
                    // 使用参考代码的回弹逻辑
                    val currentDragCard = dragCardData
                    currentDragCard?.apply {
                        offsetX = 0f
                        offsetY = 0f
                        needsAnimation = true
                        animationKey++
                    }

                    // 清理定时器防止重复
                    currentDragCard?.animCleanupTimer?.let { clearTimeout(it) }
                    currentDragCard?.animCleanupTimer = setTimeout((config.dragAnimationDuration * 1000).toInt() + 50) {
                        currentDragCard?.needsAnimation = false
                        if (lastEditingState && config.shakeEnabled) startShakeAnimation()
                    }
                }

                resetDragState()
            }
        }
    }

    private fun applySmoothTransition(
        dragCard: WidgetGridItemData,
        oldVisualPositions: Map<WidgetGridItemData, GridPosition>
    ) {
        cardList.forEach { card ->
            val oldVisualPos = oldVisualPositions[card] ?: return@forEach
            val newPos = card.pos // 此时已经是新布局下的基准位置

            // 计算需要补偿的偏移量：起点(旧视觉) - 终点(新基准)
            val startOffsetX = oldVisualPos.x - newPos.x
            val startOffsetY = oldVisualPos.y - newPos.y

            // 如果差异极小，跳过动画
            if (kotlin.math.abs(startOffsetX) < 0.1f && kotlin.math.abs(startOffsetY) < 0.1f) {
                card.offsetX = 0f
                card.offsetY = 0f
                card.needsAnimation = false
                return@forEach
            }

            // 1. 设置初始偏移 (让卡片视觉上停留在旧位置)
            card.offsetX = startOffsetX
            card.offsetY = startOffsetY

            // 2. 标记需要动画
            card.needsAnimation = true
            card.animationKey++

            // 3. 清理旧定时器
            card.animCleanupTimer?.let { clearTimeout(it) }

            // 4. 下一帧归零偏移，触发动画播放
            // 使用 setTimeout(16) 确保浏览器/引擎已经完成了上一帧的 layout 计算
            card.animCleanupTimer = setTimeout(16) {
                if (cardList.contains(card)) {
                    card.offsetX = 0f
                    card.offsetY = 0f
                    // 动画时长后清理标志
                    val durationMs = (config.dragAnimationDuration * 1000).toInt() + 50
                    card.animCleanupTimer = setTimeout(durationMs) {
                        if (cardList.contains(card)) {
                            card.needsAnimation = false
                        }
                    }
                }
            }
        }
    }

    private fun startDragging(params: PanGestureParams, cardData: WidgetGridItemData, visualIndex: Int) {
        isDragging = true
        dragCardData = cardData
        dragStartX = params.pageX
        dragStartY = params.pageY
        lastTargetVisualIndex = visualIndex
        dragStartVisualIndex = visualIndex

        // 记录起始位置（虽然现在存在 cardData.pos 里，但保留这个变量用于调试或逻辑参考）
        dragStartLogicalPos = cardData.pos

        cardData.isDragging = true
        cardData.needsAnimation = false
        cardData.shakeAngle = 0f
        cardData.shakeKey++
    }

    private fun findTargetVisualIndex(cardData: WidgetGridItemData, currentVisualIndex: Int, deltaX: Float, deltaY: Float): Int {
        if (visualOrder.size <= 1) return currentVisualIndex
        // 使用 cardData.pos 获取当前位置，或者重新计算（两者此时应一致）
        val currentPos = cardData.pos
        val dragW = getItemWidth(cardData)
        val dragH = config.cardHeight
        val dragLeft = currentPos.x + deltaX
        val dragTop = currentPos.y + deltaY
        val dragRight = dragLeft + dragW
        val dragBottom = dragTop + dragH
        val dragArea = dragW * dragH
        val cellWidth = getCardWidth() + config.cardSpacing
        val cellHeight = config.cardHeight + config.cardSpacing

        val tempVisualOrder = visualOrder.filterIndexed { idx, _ -> idx != currentVisualIndex }
        var row = 0
        var col = 0
        var targetIndex = tempVisualOrder.size

        for ((tempVisIdx, originalIdx) in tempVisualOrder.withIndex()) {
            if (originalIdx >= cardList.size) continue
            val card = cardList[originalIdx]
            val spanX = card.spanX
            if (col + spanX > config.columnCount) { row++; col = 0 }

            val slotW = if (spanX == 2) getCardWidth() * 2 + config.cardSpacing else getCardWidth()
            val slotH = config.cardHeight
            val slotLeft = col * cellWidth
            val slotTop = row * cellHeight
            val slotRight = slotLeft + slotW
            val slotBottom = slotTop + slotH

            val intersectLeft = maxOf(dragLeft, slotLeft)
            val intersectTop = maxOf(dragTop, slotTop)
            val intersectRight = minOf(dragRight, slotRight)
            val intersectBottom = minOf(dragBottom, slotBottom)

            var overlapArea = 0f
            if (intersectRight > intersectLeft && intersectBottom > intersectTop) {
                overlapArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
            }

            if (overlapArea / dragArea > 0.30f) return tempVisIdx

            col += spanX
            if (col > config.columnCount) { row++; col = 0 }
        }
        return targetIndex
    }

    private fun previewReorder(fromVisIdx: Int, toVisIdx: Int, dragCard: WidgetGridItemData) {
        if (fromVisIdx == toVisIdx) {
            // 回到原位，清除其他卡片的偏移
            cardList.forEach { c ->
                if (c != dragCard && (c.offsetX != 0f || c.offsetY != 0f)) {
                    c.offsetX = 0f
                    c.offsetY = 0f
                    c.needsAnimation = true // ✅ 开启动画，让它滑回去
                    c.animationKey++
                }
            }
            return
        }

        val tempOrder = visualOrder.toMutableList()
        val movedItem = tempOrder.removeAt(fromVisIdx)
        tempOrder.add(toVisIdx, movedItem)

        cardList.forEach { card ->
            if (card == dragCard) return@forEach
            val originalIdx = cardList.indexOf(card)
            val oldVisIdx = visualOrder.indexOf(originalIdx)
            val newVisIdx = tempOrder.indexOf(originalIdx)

            if (oldVisIdx == -1 || newVisIdx == -1) return@forEach

            // 计算旧基准位置 (当前 visualOrder)
            val oldPos = calculatePositionByVisualIndex(visualOrder, oldVisIdx)
            // 计算新基准位置 (假设的 tempOrder)
            val newPos = calculatePositionByVisualIndex(tempOrder, newVisIdx)

            val targetOffsetX = newPos.x - oldPos.x
            val targetOffsetY = newPos.y - oldPos.y

            // 只有当目标偏移量发生变化时才更新，避免死循环
            if (kotlin.math.abs(card.offsetX - targetOffsetX) > 0.1f ||
                kotlin.math.abs(card.offsetY - targetOffsetY) > 0.1f) {

                card.offsetX = targetOffsetX
                card.offsetY = targetOffsetY
                card.needsAnimation = true // ✅ 关键：预览时也要开启动画，实现“推挤”效果
                card.animationKey++
            }
        }
    }

    private fun startShakeAnimation() {
        KLog.d(TAG, "startShakeAnimation: called, enabled=${config.shakeEnabled}, interval=${config.shakeInterval}, lastEditingState=$lastEditingState")
        if (!config.shakeEnabled) return
        stopShakeAnimation()
        shakeDirection = 1
        scheduleNextShake()
    }

    private fun stopShakeAnimation() {
        KLog.d(TAG, "stopShakeAnimation: called, shakeTimerRef=$shakeTimerRef")
        shakeTimerRef?.let { clearTimeout(it) }
        shakeTimerRef = null
        cardList.forEach { c ->
            val oldKey = c.shakeKey
            c.shakeAngle = 0f
            c.shakeKey++
            KLog.d(TAG, "stopShakeAnimation: card ${c.id} shakeKey $oldKey -> ${c.shakeKey}")
        }
    }

    private fun scheduleNextShake() {
        KLog.d(TAG, "scheduleNextShake: scheduling, interval=${config.shakeInterval}, lastEditingState=$lastEditingState")
        shakeTimerRef = setTimeout(config.shakeInterval) {
            KLog.d(TAG, "scheduleNextShake: timer fired, lastEditingState=$lastEditingState")
            if (!lastEditingState) {
                KLog.d(TAG, "scheduleNextShake: editing ended, skip")
                return@setTimeout
            }
            shakeDirection = -shakeDirection
            val angle = config.shakeAngleBase * shakeDirection
            cardList.forEachIndexed { index, card ->
                if (!card.isDragging) {
                    val offset = if (index % 2 == 0) config.shakeAngleOffset else -config.shakeAngleOffset
                    val newAngle = angle + offset
                    val oldKey = card.shakeKey
                    card.shakeAngle = newAngle
                    card.shakeKey++
                    KLog.d(TAG, "scheduleNextShake: card ${card.id} isDragging=${card.isDragging}, needsAnimation=${card.needsAnimation}, oldShakeKey=$oldKey, newShakeKey=${card.shakeKey}, newAngle=$newAngle")
                } else {
                    KLog.d(TAG, "scheduleNextShake: card ${card.id} isDragging=true, skip")
                }
            }
            if (lastEditingState) scheduleNextShake()
        }
    }
    private fun startDraggingFromTouch(cardData: WidgetGridItemData, startX: Float, startY: Float) {
        val visualIndex = visualOrder.indexOf(cardList.indexOf(cardData))
        if (visualIndex < 0) return
        isDragging = true
        dragCardData = cardData
        dragStartX = startX
        dragStartY = startY
        lastTargetVisualIndex = visualIndex
        dragStartVisualIndex = visualIndex
        cardData.isDragging = true
        cardData.needsAnimation = false
        cardData.shakeAngle = 0f
        cardData.shakeKey++
        // 停止抖动动画
        stopShakeAnimation()
        // 通知外部拖拽开始（用于禁用 Scroller 滚动）
        event.fireDragStateChanged(true)
    }

    private fun updateDragFromTouch(cardData: WidgetGridItemData, deltaX: Float, deltaY: Float) {
        if (!isDragging || dragCardData != cardData) return
        cardData.offsetX = deltaX
        cardData.offsetY = deltaY

        val currentVisualIndex = visualOrder.indexOf(cardList.indexOf(cardData))
        if (currentVisualIndex < 0) return

        val targetVisualIndex = findTargetVisualIndex(cardData, currentVisualIndex, deltaX, deltaY)
        if (targetVisualIndex != lastTargetVisualIndex) {
            lastTargetVisualIndex = targetVisualIndex
            previewReorder(currentVisualIndex, targetVisualIndex, cardData)
        }
    }

    private fun endDraggingFromTouch(cardData: WidgetGridItemData) {
        if (!isDragging || dragCardData != cardData) {
            resetDragState()
            return
        }

        val targetVisualIndex = lastTargetVisualIndex
        val startVisIdx = dragStartVisualIndex

        if (startVisIdx < 0 || startVisIdx >= visualOrder.size) {
            resetDragState()
            return
        }

        // 停止抖动
        stopShakeAnimation()

        // 记录旧位置用于动画
        val oldVisualPositions = cardList.associateWith { card ->
            GridPosition(
                x = card.pos.x + card.offsetX,
                y = card.pos.y + card.offsetY,
                row = card.pos.row,
                col = card.pos.col
            )
        }

        cardList.forEach { c ->
            c.isDragging = false
            c.shakeAngle = 0f
            c.shakeKey++
        }

        if (targetVisualIndex != startVisIdx && targetVisualIndex in 0 until visualOrder.size) {
            // 更新视觉顺序
            val newOrder = visualOrder.toMutableList()
            val movedOriginalIndex = newOrder.removeAt(startVisIdx)
            newOrder.add(targetVisualIndex, movedOriginalIndex)
            visualOrder = newOrder

            // 重新计算基准位置
            updateAllPositionsBase()

            // 应用平滑过渡动画
            applySmoothTransition(dragCardData!!, oldVisualPositions)

            // 触发重排事件
            event.fireReorder(startVisIdx, targetVisualIndex)

            // 延迟恢复抖动
            resumeShakeTimerRef = setTimeout((config.dragAnimationDuration * 1000).toInt() + 100) {
                if (lastEditingState && config.shakeEnabled) startShakeAnimation()
            }
        } else {
            // 未移动，回弹
            KLog.d(TAG, "endDraggingFromTouch: 未移动分支，准备回弹")
            dragCardData?.apply {
                offsetX = 0f
                offsetY = 0f
                needsAnimation = true
                animationKey++
                KLog.d(TAG, "endDraggingFromTouch: 复位拖拽卡片 id=${id}")
            }

            // 延迟清除 needsAnimation，延迟时间与回弹动画时长匹配
            dragCardData?.animCleanupTimer = setTimeout((config.dragAnimationDuration * 1000).toInt() + 50) {
                dragCardData?.needsAnimation = false
                if (lastEditingState && config.shakeEnabled) {
                    startShakeAnimation()
                }
            }
        }
        resetDragState()
        event.fireDragStateChanged(false)
    }

    private fun resetDragState() {
        isDragging = false
        dragCardData = null
        lastTargetVisualIndex = -1
        dragStartVisualIndex = -1
        // 清理长按定时器
        longPressTimer?.let { clearTimeout(it) }
        longPressTimer = null
    }
    private fun setEditingInternal(editing: Boolean) {
        KLog.d(TAG, "setEditingInternal: old=$lastEditingState, new=$editing")
        if (lastEditingState == editing) {
            KLog.d(TAG, "setEditingInternal: 状态未变化，返回")
            return
        }
        lastEditingState = editing
        if (editing) {
            KLog.d(TAG, "setEditingInternal: 进入编辑态，启动抖动")
            startShakeAnimation()
        } else {
            KLog.d(TAG, "setEditingInternal: 退出编辑态，停止抖动")
            stopShakeAnimation()
        }
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { ctx.setEditingInternal(ctx.attr.editing) }

            View {  // 网格容器
                attr {
                    height(ctx.calculateTotalRows() * (ctx.config.cardHeight + ctx.config.cardSpacing))
                    width(ctx.attr.gridWidth)
                }

                vforIndex({ ctx.cardList }) { cardData, originalIndex, _ ->
                    View {
                        attr {
                            absolutePosition(0f, 0f)
                            size(ctx.getItemWidth(cardData), ctx.config.cardHeight)
                            zIndex(if (cardData.isDragging) 50 else 0)
                        }

                        // ================= 动画与变换核心 =================
                        attr {
                            // 最终渲染坐标 = 基准位置(pos) + 偏移量(offset)
                            val renderX = cardData.pos.x + cardData.offsetX
                            val renderY = cardData.pos.y + cardData.offsetY

                            // 确定旋转角度 (仅在非拖拽且编辑态下生效)
                            val rotateAngle = if (!cardData.isDragging && ctx.attr.editing && ctx.config.shakeEnabled) {
                                cardData.shakeAngle
                            } else {
                                0f
                            }

                            // 应用变换
                            transform(
                                translate = Translate(0f, 0f, renderX, renderY),
                                scale = if (cardData.isDragging) Scale(ctx.config.dragScaleRatio, ctx.config.dragScaleRatio) else Scale(1f, 1f),
                                rotate = Rotate(rotateAngle)
                            )

                            if (cardData.isDragging) {
                                opacity(ctx.config.dragOpacity)
                            } else {
                                opacity(1f)
                            }

                            // ================= 动画触发逻辑 =================
                            KLog.d(TAG, "动画前状态: id=${cardData.id}, isDragging=${cardData.isDragging}, needsAnimation=${cardData.needsAnimation}, shakeKey=${cardData.shakeKey}, shakeAngle=${cardData.shakeAngle}, editing=${ctx.attr.editing}, shakeEnabled=${ctx.config.shakeEnabled}")
                            if (cardData.needsAnimation) {
                                KLog.d(TAG, "动画分支 A: needsAnimation=true, id=${cardData.id}, animationKey=${cardData.animationKey}, offset=(${cardData.offsetX},${cardData.offsetY})")
                                animate(
                                    Animation.springEaseInOut(ctx.config.dragAnimationDuration, 1f, 0f),
                                    cardData.animationKey
                                )
                            } else if (!cardData.isDragging && ctx.attr.editing && ctx.config.shakeEnabled) {
                                // 读取 shakeKey 建立响应式依赖
                                val shakeKeyVal = cardData.shakeKey
                                KLog.d(TAG, "动画分支 B: 抖动动画, id=${cardData.id}, shakeKey=$shakeKeyVal, shakeAngle=${cardData.shakeAngle}")

                                if (!pagerData.isIOS) {
                                    animate(Animation.easeInOut(ctx.config.shakeAnimationDuration), shakeKeyVal)
                                }
                            }
                        }


                        // 卡片内容（直接填充，无需偏移）
                        View {
                            attr {
                                absolutePosition(0f, 0f, 0f, 0f)
                                backgroundColor(ctx.config.cardBackgroundColor)
                                borderRadius(ctx.config.cardBorderRadius)
                            }
                            ctx.attr._cardContentBuilder?.invoke(this, cardData)
                        }

                        // 删除按钮（绝对定位在卡片左上角）
                        vif({ ctx.attr.editing &&cardData.id!==1}) {
                            Image {
                                attr {
                                    absolutePosition(0f+ctx.config.deleteButtonOffset, 0f+ctx.config.deleteButtonOffset)
                                    size(ctx.config.deleteButtonSize, ctx.config.deleteButtonSize)
                                    borderRadius(ctx.config.deleteButtonSize / 2)
                                    src("assets://common/delete.png")
                                    zIndex(100)
                                }
                                event {
                                    click {
                                        ctx.event.fireDelete(item = cardData)
                                    }
                                }
                            }
                        }

                        // 拖拽手势层
                        // 编辑态交互层：使用 touch 事件实现长按拖拽
                        vif({ ctx.attr.editing }) {
                            View {
                                attr {
                                    absolutePositionAllZero()
                                    zIndex(99)
                                    backgroundColor(Color.TRANSPARENT)
                                }
                                event {
                                    touchDown { touchParams ->
                                        KLog.d(TAG,"触发touch事件")
                                        // 清除之前的定时器
                                        ctx.longPressTimer?.let { clearTimeout(it) }
                                        // 记录起始点
                                        cardData.touchDownX = touchParams.x
                                        cardData.touchDownY = touchParams.y
                                        cardData.touchDownPageX = touchParams.pageX
                                        cardData.touchDownPageY = touchParams.pageY
                                        cardData.longPressFired = false
                                        // 启动长按定时器
                                        ctx.longPressTimer = setTimeout(ctx.longPressDelay) {
                                            // 长按触发，开始拖拽
                                            if (!ctx.isDragging && !cardData.longPressFired) {
                                                cardData.longPressFired = true
                                                KLog.d(TAG,"触发长按开始拖拽")
                                                ctx.startDraggingFromTouch(cardData, touchParams.pageX, touchParams.pageY)
                                            }
                                            ctx.longPressTimer = null
                                        }
                                    }
                                    touchMove { touchParams ->
                                        if (ctx.isDragging && ctx.dragCardData == cardData) {
                                            // 已经在拖拽中，更新偏移
                                            val deltaX = touchParams.pageX - ctx.dragStartX
                                            val deltaY = touchParams.pageY - ctx.dragStartY
                                            ctx.updateDragFromTouch(cardData, deltaX, deltaY)
                                            KLog.d(TAG,"正在拖拽")
                                        } else if (ctx.longPressTimer != null && !cardData.longPressFired) {
                                            // 未触发长按时，检查移动距离是否超出阈值，若是则取消长按
                                            val dx = touchParams.pageX - cardData.touchDownPageX
                                            val dy = touchParams.pageY - cardData.touchDownPageY
                                            if (dx * dx + dy * dy > ctx.longPressMoveThreshold * ctx.longPressMoveThreshold) {
                                                clearTimeout(ctx.longPressTimer!!)
                                                ctx.longPressTimer = null
                                                KLog.d(TAG,"移动距离超过阈值取消长按")
                                            }
                                        }
                                        // 注意：不调用 stopPropagation，未拖拽时事件会继续传递给 Scroller 实现滚动
                                    }
                                    touchUp { touchParams ->
                                        KLog.d(TAG, "touchUp: card=${cardData.id}, isDragging=${ctx.isDragging}, dragCard=${ctx.dragCardData?.id}")
                                        // 清除定时器
                                        ctx.longPressTimer?.let { clearTimeout(it) }
                                        ctx.longPressTimer = null
                                        if (ctx.isDragging && ctx.dragCardData == cardData) {
                                            // 结束拖拽
                                            ctx.endDraggingFromTouch(cardData)
                                            KLog.d(TAG,"结束拖拽")
                                        }
                                        // 重置标记
                                        cardData.longPressFired = false
                                        cardData.touchDownX = 0f
                                        cardData.touchDownY = 0f
                                        cardData.touchDownPageX = 0f
                                        cardData.touchDownPageY = 0f
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun createAttr(): WidgetGridAttr = WidgetGridAttr()
    override fun createEvent(): WidgetGridEvent = WidgetGridEvent()
}

fun ViewContainer<*, *>.WidgetGrid(init: WidgetGridView.() -> Unit) {
    addChild(WidgetGridView(), init)
}