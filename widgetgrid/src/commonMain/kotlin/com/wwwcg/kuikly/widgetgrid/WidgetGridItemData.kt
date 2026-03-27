package com.wwwcg.kuikly.widgetgrid.demo


import com.tencent.kuikly.core.base.BaseObject
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.base.event.Touch
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.CallbackRef

/**
 * 网格卡片数据基类
 *
 * 业务方可以继承此类来添加自定义属性（如标题、图标颜色等），
 * 自定义属性建议使用 [observable] 委托以支持响应式更新。
 *
 * 示例：
 * ```
 * class MyCardData(scope: PagerScope) : WidgetGridItemData(scope) {
 *     var title: String by observable("")
 *     var iconColor: Color by observable(Color.BLUE)
 * }
 * ```
 *
 * @param scope PagerScope，通常传入 Pager 的 this
 */
open class WidgetGridItemData(scope: PagerScope) : BaseObject(), PagerScope by scope {

    /** 卡片唯一标识 */
    open var id: Int = 0

    /** 卡片横向占位格数，1 = 1x1，2 = 2x1 */
    open var spanX: Int by observable(1)
    var animCleanupTimer: CallbackRef? by observable(null)
    var pos  by observable(GridPosition(0f, 0f, 0, 0))

    // ==================== 内部状态（由 WidgetGrid 管理，外部请勿修改） ====================

    internal var offsetX: Float by observable(0f)
    internal var offsetY: Float by observable(0f)
    // 新增：用于强制触发布局更新的版本号
    internal var layoutVersion: Int by observable(0)
    internal var isReadyToDrag: Boolean by observable(false) // 新增：长按成功，准备拖拽
    internal var isDragging: Boolean by observable(false)
    internal var needsAnimation: Boolean by observable(false)
    internal var animationKey: Int by observable(0)
    internal var shakeAngle: Float = 0f
    internal var shakeKey: Int by observable(0)
    /** 手指是否按下 */
    internal var isTouching: Boolean by observable(false)
    var touchDownX by observable(0f)
    var touchDownY by observable(0f)


    /** 长按定时器引用，用于随时取消 */
    internal var longPressCallback: CallbackRef? = null

    /** 标记长按是否已经触发过（防止抬起时重复触发点击） */
    internal var longPressFired: Boolean = false

    /** 按下时的 X 坐标，用于计算移动距离 */
    internal var touchDownPageX: Float = 0f

    /** 按下时的 Y 坐标，用于计算移动距离 */
    internal var touchDownPageY: Float = 0f

    /** 标记在当前的触摸序列中，是否发生了明显的位移（用于区分点击和拖拽） */
    internal var wasPanned: Boolean = false

    /** 标记是否点击了删除/调整按钮（防止点击按钮后还触发卡片点击或拖拽逻辑） */
    internal var buttonClicked: Boolean = false
}
data class TouchParams(
    val x: Float, // 触摸点在自身view坐标系下的坐标X
    val y: Float, // 触摸点在自身view坐标系下的坐标Y
    val pageX: Float, // 触摸点在根视图Page下的坐标X
    val pageY: Float, // 触摸点在根视图Page下的坐标Y
    val timestamp: Long, // 触发事件时，距离系统启动的毫秒数
    val pointerId: Int, // 触摸点的ID
    val action: String, // 事件类型, 该属性从1.1.86版本开始支持，之前的版本获取为空
    val touches: List<Touch>, // 包含所有多指触摸信息
    val consumed: Boolean // 是否已经被消费了，来自渲染层的消费状态，目前用于滑动中
) {
    companion object {
        fun decode(params: Any?): TouchParams {
            val tempParams = params as? JSONObject ?: JSONObject()
            val x = tempParams.optDouble("x").toFloat()
            val y = tempParams.optDouble("y").toFloat()
            val pageX = tempParams.optDouble("pageX").toFloat()
            val pageY = tempParams.optDouble("pageY").toFloat()
            val timestamp = tempParams.optDouble("timestamp").toLong()
            val pointerId = tempParams.optInt("pointerId")
            val action =  tempParams.optString("action")
            val consumed = tempParams.optInt("consumed", 0) == 1
            val touches = arrayListOf<Touch>()
            tempParams.optJSONArray("touches")?.also {
                for (i in 0 until it.length()) {
                    touches.add(Touch.decode(it.opt(i)))
                }
            }
            return TouchParams(x, y, pageX, pageY, timestamp, pointerId, action, touches, consumed)
        }
    }
}
