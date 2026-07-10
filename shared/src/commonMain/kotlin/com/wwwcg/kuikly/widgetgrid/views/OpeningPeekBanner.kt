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
            // 统一动画：不再区分数据状态，固定使用 Spring 动画
            val globalAnim = Animation.springEaseOut(1.2f, 0.85f, 0f)

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

                // 1. 底层：内容区域 (默认图)
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

                // 2. 顶层：覆盖层（开屏缩小组件）
                vif ({ !ctx.isTransitionFinished }) {
                    OpeningBanner {
                        attr {
                            positionType(FlexPositionType.ABSOLUTE)
                            zIndex(10)
                            width(pagerData.pageViewWidth)

                            if (ctx.isShrinkStarted) {
                                // 收缩状态：回到组件原始位置
                                top(8f)
                                height(ctx.attr.shrinkHeight)
                            } else {
                                // 展开状态：负位移置顶，高度保持 expandHeight
                                top(-topGap)
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
