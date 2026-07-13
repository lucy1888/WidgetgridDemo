package com.wwwcg.kuikly.widgetgrid.views

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.View

/**
 * OpeningPeekBanner 业务组件修复版
 * 优化重点：配合 OpeningBanner 提供稳定的动画体验
 */
class OpeningPeekBannerView : ComposeView<OpeningPeekBannerAttr, OpeningPeekBannerEvent>() {
    private var isShrinkStarted by observable(false)
    private var isTransitionFinished by observable(false)

    private var defaultImageName = "peekBanner.png"

    override fun createAttr() = OpeningPeekBannerAttr()
    override fun createEvent() = OpeningPeekBannerEvent()

    override fun body(): ViewBuilder {
        val ctx = this

        return {
            // 优化动画：使用更柔和的 Spring 参数 (1.0s, 0.9 阻尼)，配合透明度渐变
            val globalAnim = Animation.springEaseOut(1.0f, 0.9f, 0f)

            // 核心逻辑：使用 statusBarHeight + 44 导航栏高度
            val topGap = ctx.pagerData.statusBarHeight + 44f

            View {
                attr {
                    width(pagerData.pageViewWidth)

                    // 动态调整容器高度
                    val targetHeight = if (ctx.isShrinkStarted) {
                        ctx.attr.shrinkHeight + 8f
                    } else {
                        ctx.attr.expandHeight - topGap
                    }
                    height(targetHeight)

                    animate(globalAnim, ctx.isShrinkStarted)
                    // 展开状态下允许溢出，确保 OpeningBanner 向上平移时不被父容器剪裁
                    overflow(false)
                }

                // 1. 底层：内容区域 (默认图，在大图消失后显露)
                View {
                    attr {
                        zIndex(1)
                        marginTop(8f)
                        width(pagerData.pageViewWidth)
                        height(ctx.attr.shrinkHeight)
                        justifyContentCenter()
                        alignItemsCenter()
                    }
                    Image {
                        attr {
                            width(343f)
                            height(ctx.attr.shrinkHeight)
                            src(ImageUri.commonAssets(ctx.defaultImageName))
                            borderRadius(8f)
                        }
                    }
                }

                // 2. 顶层：覆盖层（开屏大图，渐渐从顶部消失）
                vif ({ !ctx.isTransitionFinished }) {
                    OpeningBanner {
                        attr {
                            positionType(FlexPositionType.ABSOLUTE)
                            zIndex(10)
                            width(pagerData.pageViewWidth)

                            if (ctx.isShrinkStarted) {
                                // 收缩并渐渐消失：向上平移出视野，并透明度降为 0
                                top(-topGap - 40f) 
                                opacity(0f)
                                height(ctx.attr.expandHeight)
                            } else {
                                // 展开状态
                                top(-topGap)
                                opacity(1f)
                                height(ctx.attr.expandHeight)
                            }

                            animate(globalAnim, ctx.isShrinkStarted)
                            expandHeight = ctx.attr.expandHeight
                            shrinkHeight = ctx.attr.shrinkHeight
                        }
                        event {
                            register("onShrinkStarted") { ctx.isShrinkStarted = true }
                            register("onShrinkFinished") { ctx.isTransitionFinished = true }
                        }
                    }
                }
            }
        }
    }
}


class OpeningPeekBannerAttr : ComposeAttr() {
    var expandHeight: Float = 400f
    var shrinkHeight: Float = 64f
}

class OpeningPeekBannerEvent : ComposeEvent()

fun ViewContainer<*, *>.OpeningPeekBanner(init: OpeningPeekBannerView.() -> Unit) {
    addChild(OpeningPeekBannerView(), init)
}
