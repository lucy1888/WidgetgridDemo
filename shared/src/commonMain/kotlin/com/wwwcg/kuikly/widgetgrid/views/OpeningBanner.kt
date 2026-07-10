package com.wwwcg.kuikly.widgetgrid.views

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.views.*
import com.tencent.kuikly.core.views.layout.Row
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 开屏/顶部大图缩小组件
 * 修复版：移除业务依赖，保持兜底逻辑
 */
class OpeningBannerView : ComposeView<OpeningBannerAttr, OpeningBannerEvent>() {
    var isShrinked by observable(false)
    var showCountdown by observable(true)
    var remainingTime by observable(8)
    private var timerId: String = ""

    override fun createAttr() = OpeningBannerAttr()
    override fun createEvent() = OpeningBannerEvent()

    override fun viewDidLoad() {
        super.viewDidLoad()
        startCountdown()
    }

    private fun startCountdown() {
        if (isShrinked) return
        timerId = setTimeout(1000) {
            if (remainingTime > 1) {
                remainingTime--
                startCountdown()
            } else {
                remainingTime = 0
                shrink()
            }
        }
    }

    fun shrink() {
        if (isShrinked) return
        showCountdown = false
        isShrinked = true
        clearTimeout(timerId)

        event.onShrinkStarted(JSONObject())

        setTimeout(1200) {
            event.onShrinkFinished(JSONObject().apply {
                put("isShrinked", true)
            })
        }
    }

    override fun body(): ViewBuilder {
        val ctx = this
        var startY = 0f

        return {
            // 统一使用带有数据的 Spring 动画，确保视觉体验一致
            val layoutAnim = Animation.springEaseOut(1.2f, 0.85f, 0f)

            // 动态计算当前高度
            val targetHeight = if (ctx.isShrinked) ctx.attr.shrinkHeight else ctx.attr.expandHeight

            View {
                attr {
                    width(pagerData.pageViewWidth)
                    height(targetHeight)
                    overflow(true)
                    animate(layoutAnim, ctx.isShrinked)
                }
                event {
                    pan { params ->
                        if (ctx.isShrinked) return@pan
                        when (params.state) {
                            "start" -> startY = params.pageY
                            "move" -> {
                                val dy = params.pageY - startY
                                if (dy < -30f) {
                                    ctx.showCountdown = false
                                    ctx.shrink()
                                }
                            }
                        }
                    }
                }

                Row {
                    attr {
                        width(pagerData.pageViewWidth)
                        height(targetHeight)
                        
                        if (ctx.isShrinked) {
                            justifyContentCenter()
                            alignItemsCenter()
                        }
                        
                        animate(layoutAnim, ctx.isShrinked)
                    }

                    // 1. 兜底 PAG
                    PAG {
                        attr {
                            src(ImageUri.commonAssets("hangxiaobao.pag"))
                            autoPlay(true)
                            repeatCount(-1)

                            width(pagerData.pageViewWidth)
                            height(ctx.attr.expandHeight)
                            borderRadius(if (ctx.isShrinked) 8f else 0f)

                            opacity(if (ctx.isShrinked) 0f else 1f)
                            animate(layoutAnim, ctx.isShrinked)
                        }
                    }
                }

                vif ({ctx.showCountdown}) {
                    View {
                        attr {
                            positionAbsolute()
                            bottom(12f)
                            left(12f)
                            backgroundColor(Color(0x66000000))
                            borderRadius(4f)
                            padding(4f, 8f, 4f, 8f)
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        Text {
                            attr {
                                text("${ctx.remainingTime}s上滑收起")
                                color(Color.WHITE)
                                fontSize(12f)
                            }
                        }
                    }
                }
            }
        }
    }
}

class OpeningBannerAttr : ComposeAttr() {
    var expandHeight: Float = 400f
    var shrinkHeight: Float = 64f
}

class OpeningBannerEvent : ComposeEvent() {
    fun onShrinkStarted(data: JSONObject) {
        onFireEvent("onShrinkStarted", data)
    }

    fun onShrinkFinished(data: JSONObject) {
        onFireEvent("onShrinkFinished", data)
    }
}

fun ViewContainer<*, *>.OpeningBanner(init: OpeningBannerView.() -> Unit) {
    addChild(OpeningBannerView(), init)
}
