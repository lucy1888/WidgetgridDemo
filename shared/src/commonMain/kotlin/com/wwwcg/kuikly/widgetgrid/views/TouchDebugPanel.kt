package com.wwwcg.kuikly.widgetgrid.views


import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 触摸事件调试面板（仅用于给厂家排查 Scroller 消费 touch 事件的 bug）
 *
 * 复现场景：父容器 Scroller 内嵌的 View 绑定 touchDown/touchMove/touchUp，
 * 在列表底部触发上滑手势时，部分机型的 Scroller 会消费 touchMove 事件，
 * 导致 touchMove dy 全部为正，方向判断失败。
 *
 * 使用方法：
 * 1. 滚动到页面底部看到绿色"底部触发区域"
 * 2. 在该区域做上滑手势
 * 3. 观察实时数据显示：
 *    - 绿色 = touchMove 方向正确识别
 *    - 红色 = touchMove 被 Scroller 消费，方向判断异常
 *    - 灰色 = 等待手势
 *
 * 底部面板实时显示每次手势的详细字段：
 *   touchDown.pageY / touchUp.pageY / totalDy
 *   touchMove触发次数 / 检测到上滑方向 / hasSeenTouchMove
 */

/**
 * 异常原因枚举
 */
internal enum class AnomalyReason(val label: String) {
    NO_TOUCH_MOVE_CANCEL("touchMove完全未触发(Scroller立即消费)"),
    NO_TOUCH_MOVE_UP("touchMove完全未触发(无有效滑动)"),
    ZERO_DISPLACEMENT_CANCEL("touchMove触发但位移为0(Scroller拦截)"),
    ZERO_DISPLACEMENT_UP("总位移为0(无有效滑动)"),
    POSITIVE_DY_CANCEL("dy为正(Scroller bounce干扰)"),
    POSITIVE_DY_UP("dy为正(方向错误，预期上滑)"),
    INSUFFICIENT_UP_DY("上滑位移不足(totalDy>-10f)"),
    UNKNOWN("未知原因"),
}

internal class TouchDebugPanel : ComposeView<TouchDebugAttr, ComposeEvent>() {

    // ---- 触摸状态 ----
    private var touchStartY = 0f
    private var touchLastY = 0f
    private var hasSeenTouchMove = false
    private var isTouchSwipeUp = false
    private var touchMoveCount = 0

    // ---- UI 显示数据 ----
    private var lastTotalDy by observable(0f)
    private var lastResult by observable("等待手势...")
    private var lastResultColor by observable(0xFF999999L) // 默认灰色
    private var gestureCount by observable(0)
    private var successCount by observable(0)
    private var failCount by observable(0)

    // ---- 事件类型统计（核心：暴露 touchUp 从未触发的 bug） ----
    private var touchDownEventCount by observable(0)
    private var touchUpEventCount by observable(0)
    private var touchCancelEventCount by observable(0)
    private var touchMoveTotalCount by observable(0)
    /** 最近一次手势的事件流向，如 "DN→MV(3)→CANCEL" */
    private var lastEventFlow by observable("")
    /** 最近一次手势的结束类型: "TOUCH_UP" / "TOUCH_CANCEL" */
    private var lastEventEndType by observable("")

    // ---- 异常原因分类统计 ----
    /** 最近一次异常的详细原因 */
    private var lastAnomalyReason by observable("")
    /** 各异常原因的累计次数，key=AnomalyReason.label */
    private var anomalyBreakdown = mutableMapOf<String, Int>().also { map ->
        AnomalyReason.entries.forEach { map[it.label] = 0 }
    }

    // debug 日志面板数据
    private var debugTouchDownPageY by observable(0f)
    private var debugTouchUpPageY by observable(0f)
    private var debugTouchMoveCount by observable(0)
    private var debugDetectedUp by observable(false)
    private var debugHasSeenMove by observable(false)
    private var debugResult by observable("")

    // 结果颜色常量（Long 类型，可直接传给 Color() 或 color()）
    companion object {
        private const val COLOR_GREEN = 0xFF4CAF50L
        private const val COLOR_RED = 0xFFF44336L
        private const val COLOR_GRAY = 0xFFBDBDBDL
    }

    /**
     * 异常原因分类
     * 根据 isCancel / hasSeenTouchMove / totalDy 的组合确定具体异常原因
     */
    private fun classifyAnomaly(isCancel: Boolean, totalDy: Float): AnomalyReason {
        if (!hasSeenTouchMove) {
            return if (isCancel) AnomalyReason.NO_TOUCH_MOVE_CANCEL
            else AnomalyReason.NO_TOUCH_MOVE_UP
        }
        if (totalDy == 0f) {
            return if (isCancel) AnomalyReason.ZERO_DISPLACEMENT_CANCEL
            else AnomalyReason.ZERO_DISPLACEMENT_UP
        }
        if (totalDy > 0f) {
            return if (isCancel) AnomalyReason.POSITIVE_DY_CANCEL
            else AnomalyReason.POSITIVE_DY_UP
        }
        // totalDy < 0 && totalDy >= -10f，仅 touchUp 路径能走到这里
        // cancel 路径 totalDy<0 已经判为成功
        return AnomalyReason.INSUFFICIENT_UP_DY
    }

    /**
     * 判定手势结果：touchUp 和 touchCancel 共用
     * @param endPageY 手势结束时的 pageY（touchUp 用 params.pageY，touchCancel 用最后的 touchLastY）
     * @param isCancel 是否为 touchCancel（Scroller拦截），cancel时放宽判定阈值
     */
    private fun evaluateGesture(endPageY: Float, isCancel: Boolean) {
        val totalDy = endPageY - touchStartY
        debugTouchUpPageY = endPageY
        gestureCount++

        val isEffectiveUp = isTouchSwipeUp
        // cancel 场景：Scroller bounce 必然产生正 dy，所以 totalDy<0 就是真上滑
        // touchUp 场景：手势完整，用更大的阈值避免误触
        val isFallbackUp = if (isCancel) totalDy < 0f else totalDy < -10f
        val success = isEffectiveUp || isFallbackUp

        lastTotalDy = totalDy

        val suffix = if (isCancel) "cancel" else "up"

        if (success) {
            lastResult = "成功识别上滑"
            lastResultColor = COLOR_GREEN
            successCount++
            lastAnomalyReason = ""
            debugResult = "成功($suffix) (touchSwipeUp=$isEffectiveUp, fallback=$isFallbackUp)"
            KLog.d("TouchDebugPanel", "[Debug] touch$suffix 成功: totalDy=$totalDy, touchSwipeUp=$isEffectiveUp, touchMoveCount=$touchMoveCount")
        } else {
            lastResult = "异常：方向判断失败"
            lastResultColor = COLOR_RED
            failCount++

            val reason = classifyAnomaly(isCancel, totalDy)
            lastAnomalyReason = reason.label
            // 累计异常原因统计
            anomalyBreakdown[reason.label] = (anomalyBreakdown[reason.label] ?: 0) + 1

            debugResult = "异常($suffix): ${reason.label}"
            KLog.d("TouchDebugPanel", "[Debug] touch$suffix 异常: totalDy=$totalDy, reason=${reason.name}, touchMoveCount=$touchMoveCount, isCancel=$isCancel, hasSeenMove=$hasSeenTouchMove")
        }
    }

    override fun createAttr(): TouchDebugAttr = TouchDebugAttr()
    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            // ===== 本地 helper 函数（在 ViewBuilder lambda 内，自动继承 ViewContainer receiver） =====

            /** 构建一行 key-value 数据 */
            fun dataRow(label: String, value: String) {
                View {
                    attr {
                        flexDirectionRow()
                        justifyContentSpaceBetween()
                        marginBottom(4f)
                    }
                    Text {
                        attr {
                            text(label)
                            fontSize(12f)
                            color(Color(0xFF999999))
                        }
                    }
                    Text {
                        attr {
                            text(value)
                            fontSize(12f)
                            color(Color(0xFF333333))
                            fontWeightSemiBold()
                        }
                    }
                }
            }

            // ===== UI 结构 =====

            Scroller {
                attr {
                    width(pagerData.pageViewWidth)
                    height(pagerData.pageViewHeight)
                    backgroundColor(Color(0xFFF5F5F5))
                    bouncesEnable(false)
                }

                // ========== 标题区域 ==========
                View {
                    attr {
                        width(pagerData.pageViewWidth - 32f)
                        marginLeft(16f)
                        marginRight(16f)
                        marginTop(20f)
                        padding(16f)
                        backgroundColor(Color.WHITE)
                        borderRadius(12f)
                    }
                    Text {
                        attr {
                            text("触摸事件调试面板")
                            fontSize(18f)
                            color(Color(0xFF333333))
                            fontWeightBold()
                        }
                    }
                    Text {
                        attr {
                            text("用途：排查 Scroller 消费 touchMove 事件的 bug")
                            fontSize(13f)
                            color(Color(0xFF999999))
                            marginTop(8f)
                        }
                    }
                    Text {
                        attr {
                            text("模拟瀑布流：滑到Scroller最底部 → 在绿色区域上滑 → 观察结果")
                            fontSize(13f)
                            color(Color(0xFF999999))
                            marginTop(4f)
                        }
                    }
                }

                // ========== 统计面板 ==========
                View {
                    attr {
                        width(pagerData.pageViewWidth - 32f)
                        marginLeft(16f)
                        marginRight(16f)
                        marginTop(12f)
                        padding(16f)
                        backgroundColor(Color.WHITE)
                        borderRadius(12f)
                        flexDirectionRow()
                        justifyContentSpaceAround()
                    }
                    View {
                        attr { alignItemsCenter() }
                        Text {
                            attr {
                                text("${ctx.gestureCount}")
                                fontSize(28f)
                                color(Color(0xFF333333))
                                fontWeightBold()
                            }
                        }
                        Text {
                            attr {
                                text("总手势")
                                fontSize(12f)
                                color(Color(0xFF999999))
                                marginTop(4f)
                            }
                        }
                    }
                    View {
                        attr { alignItemsCenter() }
                        Text {
                            attr {
                                text("${ctx.successCount}")
                                fontSize(28f)
                                color(Color(0xFF4CAF50))
                                fontWeightBold()
                            }
                        }
                        Text {
                            attr {
                                text("成功")
                                fontSize(12f)
                                color(Color(0xFF999999))
                                marginTop(4f)
                            }
                        }
                    }
                    View {
                        attr { alignItemsCenter() }
                        Text {
                            attr {
                                text("${ctx.failCount}")
                                fontSize(28f)
                                color(Color(0xFFF44336))
                                fontWeightBold()
                            }
                        }
                        Text {
                            attr {
                                text("异常")
                                fontSize(12f)
                                color(Color(0xFF999999))
                                marginTop(4f)
                            }
                        }
                    }
                }

                // ========== 事件类型统计（暴露 touchUp 从未触发的 bug） ==========
                View {
                    attr {
                        width(pagerData.pageViewWidth - 32f)
                        marginLeft(16f)
                        marginRight(16f)
                        marginTop(12f)
                        padding(16f)
                        backgroundColor(Color.WHITE)
                        borderRadius(12f)
                        flexDirectionRow()
                        justifyContentSpaceAround()
                    }
                    // touchDown
                    View {
                        attr { alignItemsCenter() }
                        Text {
                            attr {
                                text("${ctx.touchDownEventCount}")
                                fontSize(22f)
                                color(Color(0xFF1565C0))
                                fontWeightBold()
                            }
                        }
                        Text { attr { text("touchDown").fontSize(12f).color(Color(0xFF999999)).marginTop(2f) } }
                    }
                    // touchMove
                    View {
                        attr { alignItemsCenter() }
                        Text {
                            attr {
                                text("${ctx.touchMoveTotalCount}")
                                fontSize(22f)
                                color(Color(0xFF7B1FA2))
                                fontWeightBold()
                            }
                        }
                        Text { attr { text("touchMove").fontSize(12f).color(Color(0xFF999999)).marginTop(2f) } }
                    }
                    // touchCancel
                    View {
                        attr { alignItemsCenter() }
                        Text {
                            attr {
                                text("${ctx.touchCancelEventCount}")
                                fontSize(22f)
                                color(
                                    if (ctx.touchUpEventCount == 0 && ctx.touchCancelEventCount > 0)
                                        Color(0xFFD32F2F)  // 异常红
                                    else Color(0xFFE65100)
                                )
                                fontWeightBold()
                            }
                        }
                        Text {
                            attr {
                                text("touchCancel")
                                fontSize(12f)
                                color(Color(0xFF999999))
                                marginTop(2f)
                            }
                        }
                    }
                    // touchUp
                    View {
                        attr { alignItemsCenter() }
                        Text {
                            attr {
                                text("${ctx.touchUpEventCount}")
                                fontSize(22f)
                                color(
                                    if (ctx.touchUpEventCount == 0 && ctx.gestureCount > 0)
                                        Color(0xFFD32F2F)  // 异常红
                                    else Color(0xFF2E7D32)
                                )
                                fontWeightBold()
                            }
                        }
                        Text {
                            attr {
                                text("touchUp")
                                fontSize(12f)
                                color(Color(0xFF999999))
                                marginTop(2f)
                            }
                        }
                    }
                }

                // ========== 🚨 警告：touchUp 从未触发 ==========
                vif({ ctx.touchUpEventCount == 0 && ctx.gestureCount > 0 }) {
                    View {
                        attr {
                            width(pagerData.pageViewWidth - 32f)
                            marginLeft(16f)
                            marginRight(16f)
                            marginTop(8f)
                            padding(12f)
                            backgroundColor(Color(0xFFFFEBEE))
                            borderRadius(8f)
                            border(Border(1.5f, BorderStyle.SOLID, Color(0xFFEF5350)))
                        }
                        Text {
                            attr {
                                text("🚨 关键BUG: ${ctx.gestureCount}次手势中 touchUp=0, touchCancel=${ctx.touchCancelEventCount}")
                                fontSize(13f)
                                color(Color(0xFFC62828))
                                fontWeightBold()
                            }
                        }
                        Text {
                            attr {
                                text("厂商需修复：Scroller全部消费了touch序列，不抛出touchUp事件")
                                fontSize(11f)
                                color(Color(0xFFE57373))
                                marginTop(4f)
                            }
                        }
                        Text {
                            attr {
                                text("cancel率=100% | 结论：手势检测完全不可用")
                                fontSize(11f)
                                color(Color(0xFFE57373))
                                marginTop(2f)
                            }
                        }
                    }
                }

                // ========== 实时数据面板 ==========
                vif({ ctx.gestureCount > 0 }) {
                    View {
                        attr {
                            width(pagerData.pageViewWidth - 32f)
                            marginLeft(16f)
                            marginRight(16f)
                            marginTop(12f)
                            padding(16f)
                            backgroundColor(
                                if (ctx.debugResult.startsWith("成功")) Color(0xFFF1F8E9)
                                else Color(0xFFFFF3E0)
                            )
                            borderRadius(12f)
                        }
                        Text {
                            attr {
                                text("最近一次手势数据")
                                fontSize(14f)
                                color(Color(0xFF333333))
                                fontWeightBold()
                                marginBottom(8f)
                            }
                        }
                        // 事件流向（高亮显示）
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                                marginBottom(8f)
                                padding(6f)
                                backgroundColor(
                                    if (ctx.lastEventEndType == "TOUCH_CANCEL") Color(0xFFFFCDD2)
                                    else Color(0xFFC8E6C9)
                                )
                                borderRadius(4f)
                            }
                            Text {
                                attr {
                                    text("事件流: ")
                                    fontSize(12f)
                                    color(Color(0xFF666666))
                                }
                            }
                            Text {
                                attr {
                                    text(ctx.lastEventFlow)
                                    fontSize(12f)
                                    color(
                                        if (ctx.lastEventEndType == "TOUCH_CANCEL") Color(0xFFC62828)
                                        else Color(0xFF2E7D32)
                                    )
                                    fontWeightBold()
                                }
                            }
                        }
                        // 结束类型
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                                marginBottom(8f)
                            }
                            Text {
                                attr {
                                    text("结束类型: ")
                                    fontSize(12f)
                                    color(Color(0xFF666666))
                                }
                            }
                            Text {
                                attr {
                                    text(
                                        when (ctx.lastEventEndType) {
                                            "TOUCH_CANCEL" -> "TOUCH_CANCEL ❌ (被Scroller消费)"
                                            "TOUCH_UP" -> "TOUCH_UP ✅ (正常)"
                                            else -> "未知"
                                        }
                                    )
                                    fontSize(12f)
                                    color(
                                        if (ctx.lastEventEndType == "TOUCH_CANCEL") Color(0xFFC62828)
                                        else Color(0xFF2E7D32)
                                    )
                                    fontWeightBold()
                                }
                            }
                        }
                        dataRow("结果", ctx.debugResult)
                        dataRow("touchDown.pageY", "${ctx.debugTouchDownPageY}")
                        dataRow("touchUp.pageY", "${ctx.debugTouchUpPageY}")
                        dataRow("totalDy", "${ctx.lastTotalDy}")
                        dataRow("touchMove触发次数", "${ctx.debugTouchMoveCount}")
                        dataRow("检测到上滑方向", "${ctx.debugDetectedUp}")
                        dataRow("hasSeenTouchMove", "${ctx.debugHasSeenMove}")
                    }
                }

                // ========== 异常原因分析（仅在有异常时展示） ==========
                vif({ ctx.failCount > 0 }) {
                    View {
                        attr {
                            width(pagerData.pageViewWidth - 32f)
                            marginLeft(16f)
                            marginRight(16f)
                            marginTop(12f)
                            padding(16f)
                            backgroundColor(Color(0xFFFBE9E7))
                            borderRadius(12f)
                        }
                        Text {
                            attr {
                                text("异常原因分析（共${ctx.failCount}次异常）")
                                fontSize(14f)
                                color(Color(0xFFD84315))
                                fontWeightBold()
                                marginBottom(8f)
                            }
                        }
                        // 展示有数据的异常原因 ${count}次
                        /** 构建一行异常原因统计 */
                        fun anomalyRow(label: String, count: Int) {
                            View {
                                attr {
                                    flexDirectionRow()
                                    justifyContentSpaceBetween()
                                    alignItemsCenter()
                                    marginBottom(4f)
                                }
                                Text {
                                    attr {
                                        text(label)
                                        fontSize(12f)
                                        color(Color(0xFF666666))
                                        flex(1f)
                                    }
                                }
                                Text {
                                    attr {
                                        text("${count}次")
                                        fontSize(12f)
                                        color(Color(0xFFD84315))
                                        fontWeightBold()
                                    }
                                }
                            }
                        }
                        val bd = ctx.anomalyBreakdown
                        val c1 = bd["touchMove完全未触发(Scroller立即消费)"] ?: 0
                        val c2 = bd["touchMove完全未触发(无有效滑动)"] ?: 0
                        val c3 = bd["touchMove触发但位移为0(Scroller拦截)"] ?: 0
                        val c4 = bd["总位移为0(无有效滑动)"] ?: 0
                        val c5 = bd["dy为正(Scroller bounce干扰)"] ?: 0
                        val c6 = bd["dy为正(方向错误，预期上滑)"] ?: 0
                        val c7 = bd["上滑位移不足(totalDy>-10f)"] ?: 0
                        val c8 = bd["未知原因"] ?: 0
                        if (c1 > 0) anomalyRow("touchMove未触发(Scroller立即消费)", c1)
                        if (c2 > 0) anomalyRow("touchMove未触发(无有效滑动)", c2)
                        if (c3 > 0) anomalyRow("touchMove触发但位移为0(Scroller拦截)", c3)
                        if (c4 > 0) anomalyRow("总位移为0(无有效滑动)", c4)
                        if (c5 > 0) anomalyRow("dy为正(Scroller bounce干扰)", c5)
                        if (c6 > 0) anomalyRow("dy为正(方向错误，预期上滑)", c6)
                        if (c7 > 0) anomalyRow("上滑位移不足(totalDy>-10f)", c7)
                        if (c8 > 0) anomalyRow("未知原因", c8)
                        // 最近一次异常详情
                        View {
                            attr {
                                marginTop(8f)
                                paddingTop(8f)
                                borderTop(
                                    Border(0.5f, BorderStyle.SOLID, Color(0xFFFFCDD2))
                                )
                            }
                            Text {
                                attr {
                                    text("最近异常: ${ctx.lastAnomalyReason}")
                                    fontSize(11f)
                                    color(Color(0xFF999999))
                                }
                            }
                        }
                    }
                }

                // ========== 异常判定标准说明 ==========
                View {
                    attr {
                        width(pagerData.pageViewWidth - 32f)
                        marginLeft(16f)
                        marginRight(16f)
                        marginTop(12f)
                        padding(16f)
                        backgroundColor(Color(0xFFFFF8E1))
                        borderRadius(12f)
                    }
                    Text {
                        attr {
                            text("面板说明")
                            fontSize(14f)
                            color(Color(0xFF333333))
                            fontWeightBold()
                            marginBottom(8f)
                        }
                    }
                    Text {
                        attr {
                            text(
                                "模拟场景：CreditCardWaterfall 瀑布流滑到底部后上滑切换页面\n" +
                                        "  · 绿色区域 = Scroller 内容的最后一个子 View（在面板说明下方）\n" +
                                        "  · 用户手动滑到 Scroller 底部的绿色区域 → 上滑测试\n\n" +
                                        "🚨 关注「事件类型统计」面板：\n" +
                                        "  · 如果 touchUp=0 但 touchCancel>0 → 致命BUG\n" +
                                        "  · Scroller 消费了全部触摸序列，永远不抛 touchUp\n" +
                                        "  · 导致手势检测完全不可用，所有上滑回调失效\n\n" +
                                        "异常判定标准:\n" +
                                        "  异常 = isTouchSwipeUp==false 且 未通过兜底阈值\n\n" +
                                        "成功条件（任一满足即可）：\n" +
                                        "  ① touchMove 中曾检测到 dy<-5f (isTouchSwipeUp=true)\n" +
                                        "  ② totalDy<0 (cancel路径) 或 totalDy<-10f (touchUp路径)\n\n" +
                                        "可识别的异常原因：\n" +
                                        "  · touchMove未触发 → Scroller立即消费了事件\n" +
                                        "  · 位移为0 → 有touchMove但手指未滑动\n" +
                                        "  · dy为正 → Scroller bounce使方向反转\n" +
                                        "  · 上滑不足 → 上滑但位移不到阈值(touchUp路径)"
                            )
                            fontSize(11f)
                            color(Color(0xFF666666))
                            lineHeight(18f)
                        }
                    }
                }

                // ========== 底部触发区域（核心调试区，模拟瀑布流底部上滑） ==========
                View {
                    attr {
                        width(pagerData.pageViewWidth - 32f)
                        marginLeft(16f)
                        marginRight(16f)
                        marginTop(12f)
                        marginBottom(40f)
                        height(160f)
                        backgroundColor(Color(0xFFE8F5E9))
                        borderRadius(12f)
                        // 边框颜色根据 lastResultColor 变化：绿=成功, 红=异常, 灰=默认
                        border(Border(2f, BorderStyle.SOLID, Color(ctx.lastResultColor)))
                        allCenter()
                    }
                    event {
                        touchDown { params ->
                            ctx.touchStartY = params.pageY
                            ctx.touchLastY = params.pageY
                            ctx.hasSeenTouchMove = false
                            ctx.isTouchSwipeUp = false
                            ctx.touchMoveCount = 0
                            ctx.debugTouchDownPageY = params.pageY
                            ctx.debugTouchMoveCount = 0
                            ctx.debugDetectedUp = false
                            ctx.debugHasSeenMove = false
                            ctx.debugResult = ""
                            ctx.touchDownEventCount++
                            ctx.lastEventFlow = "DN"
                            ctx.lastEventEndType = ""
                            KLog.d("TouchDebugPanel", "[Debug] touchDown pageY=${params.pageY}")
                        }

                        touchMove { params ->
                            ctx.hasSeenTouchMove = true
                            ctx.touchMoveCount++
                            ctx.touchMoveTotalCount++
                            ctx.debugHasSeenMove = true
                            ctx.debugTouchMoveCount = ctx.touchMoveCount

                            val dy = params.pageY - ctx.touchLastY
                            ctx.touchLastY = params.pageY

                            ctx.lastEventFlow = "DN→MV(${ctx.touchMoveCount})"

                            if (dy < -5f && !ctx.isTouchSwipeUp) {
                                ctx.isTouchSwipeUp = true
                                ctx.debugDetectedUp = true
                                KLog.d("TouchDebugPanel", "[Debug] touchMove #${ctx.touchMoveCount} 检测到上滑! dy=$dy, pageY=${params.pageY}")
                            } else if (ctx.touchMoveCount <= 5) {
                                KLog.d("TouchDebugPanel", "[Debug] touchMove #${ctx.touchMoveCount} dy=$dy, pageY=${params.pageY} (上滑=${dy < -5f})")
                            }
                        }

                        touchUp { params ->
                            ctx.touchUpEventCount++
                            ctx.lastEventEndType = "TOUCH_UP"
                            ctx.lastEventFlow = "DN→MV(${ctx.touchMoveCount})→UP"
                            KLog.d("TouchDebugPanel", "[Debug] ✅ touchUp (罕见! 本次手势未被Scroller消费)")
                            ctx.evaluateGesture(params.pageY, isCancel = false)
                        }

                        touchCancel {
                            ctx.touchCancelEventCount++
                            ctx.lastEventEndType = "TOUCH_CANCEL"
                            ctx.lastEventFlow = if (ctx.touchMoveCount > 0) {
                                "DN→MV(${ctx.touchMoveCount})→CANCEL"
                            } else {
                                "DN→CANCEL"
                            }
                            KLog.d("TouchDebugPanel", "[Debug] ❌ touchCancel (Scroller消费手势, touchMove=${ctx.touchMoveCount}次)")
                            ctx.evaluateGesture(ctx.touchLastY, isCancel = true)
                        }
                    }
                    // 提示文字
                    View {
                        attr { allCenter() }
                        Text {
                            attr {
                                text("在此区域上滑触发")
                                fontSize(16f)
                                color(Color(0xFF666666))
                                fontWeightSemiBold()
                            }
                        }
                        Text {
                            attr {
                                text("模拟瀑布流底部上滑 · Scroller 触底后才释放 touch 事件")
                                fontSize(12f)
                                color(Color(0xFF999999))
                                marginTop(4f)
                            }
                        }
                        Text {
                            attr {
                                text(ctx.lastResult)
                                fontSize(14f)
                                color(ctx.lastResultColor)
                                fontWeightBold()
                                marginTop(8f)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TouchDebugPanel 属性
 */
internal class TouchDebugAttr : ComposeAttr() {
}

/**
 * DSL 入口
 */
internal fun ViewContainer<*, *>.TouchDebugPanel(init: TouchDebugPanel.() -> Unit) {
    addChild(TouchDebugPanel(), init)
}
