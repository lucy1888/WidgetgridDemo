package com.wwwcg.kuikly.widgetgrid.demo

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.*
import com.wwwcg.kuikly.widgetgrid.base.BasePager
import com.wwwcg.kuikly.widgetgrid.views.PeekBanner

/**
 * 模拟数据模型
 */
data class MockBannerItem(
    val id: String,
    val color: Color,
    val title: String
)

/**
 * PeekBanner 演示页面
 * 参考 OpeningBannerAnimationDemoPage 格式进行修改
 */
@Page("PeekBannerDemoPage")
internal class PeekBannerDemoPage : BasePager() {

    // 使用 key 机制强制刷新组件状态（可选，用于测试重置效果）
    var demoKey by observable(0L)

    // 页面配置
    private val shrinkHeight = 80f
    private val peekDisplayTimeMs = 3000

    // 模拟数据：使用纯色块
    private val peekBanners = listOf(
        MockBannerItem("1", Color(0xFFFF5252L), "红色活动"),
        MockBannerItem("2", Color(0xFF4CAF50L), "绿色活动"),
        MockBannerItem("3", Color(0xFF2196F3L), "蓝色活动"),
        MockBannerItem("4", Color(0xFFFFC107L), "黄色活动")
    )

    // 已曝光索引记录
    private val reportedIndices = mutableSetOf<Int>()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFF5F5F5L))
            }

            // 1. 顶部状态栏占位与标题栏
            View {
                attr {
                    paddingTop(ctx.pagerData.statusBarHeight)
                    backgroundColor(Color.WHITE)
                }
                View {
                    attr {
                        height(44f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(16f)
                        paddingRight(16f)
                    }
                    // 返回按钮
                    Text {
                        attr {
                            text("←")
                            fontSize(20f)
                        }
                        event {
                            click {
                                ctx.getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                            }
                        }
                    }
                    Text {
                        attr {
                            flex(1f)
                            text("PeekBanner 演示")
                            fontSize(16f)
                            fontWeightBold()
                            textAlignCenter()
                        }
                    }
                    // 重置按钮
                    View {
                        attr {
                            paddingTop(4f)
                            paddingBottom(4f)
                            paddingLeft(12f)
                            paddingRight(12f)
                            backgroundColor(Color(0xFF0A84FFL))
                            borderRadius(12f)
                        }
                        event {
                            click {
                                ctx.demoKey = ctx.demoKey + 1
                                ctx.reportedIndices.clear()
                            }
                        }
                        Text {
                            attr {
                                text("重置")
                                color(Color.WHITE)
                                fontSize(12f)
                            }
                        }
                    }
                }
            }

            // 2. 核心演示区域
            View {
                attr {
                    flex(1f)
                }

                Text {
                    attr {
                        text("自动轮播 + 无限循环测试")
                        fontSize(14f)
                        textAlignCenter()
                        marginTop(20f)
                        width(ctx.pagerData.pageViewWidth)
                    }
                }

                // PeekBanner 组件引用
                PeekBanner {
                    attr {
                        zIndex(1)
                        marginTop(20f)
                        width = ctx.pagerData.pageViewWidth
                        pageItemWidth = 311f
                        pageItemHeight = ctx.shrinkHeight
                        
                        loopPlayIntervalTimeMs = ctx.peekDisplayTimeMs

                        initSliderItems(ctx.peekBanners) { currentItem, _ ->
                            View {
                                attr {
                                    width(311f)
                                    height(ctx.shrinkHeight)
                                    padding(0f, 8f, 0f, 8f)
                                }
                                
                                // 使用纯色块视图替代图片
                                View {
                                    attr {
                                        width(295f)
                                        height(ctx.shrinkHeight)
                                        backgroundColor(currentItem.color)
                                        borderRadius(8f)
                                    }
                                    event {
                                        click {
                                            ctx.bannerImgClick(currentItem)
                                        }
                                    }
                                    // 在色块中心显示标题
                                    Text {
                                        attr {
                                            text(currentItem.title)
                                            color(Color.WHITE)
                                            fontSize(18f)
                                            fontWeightBold()
                                            flex(1f)
                                            textAlignCenter()
                                            alignSelfCenter()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    event {
                        pageIndexDidChanged {
                            val data = it as JSONObject
                            val index = data.optInt("index")
                            if (!ctx.reportedIndices.contains(index)) {
                                ctx.peekBanners.getOrNull(index)?.let { item ->
                                    println("[曝光上报] Index: $index, Title: ${item.title}")
                                    ctx.reportedIndices.add(index)
                                }
                            }
                        }
                    }
                }
                
                Text {
                    attr {
                        text("提示：左右滑动可手动切换，停止操作后恢复自动轮播")
                        fontSize(12f)
                        color(Color(0xFF999999L))
                        marginTop(10f)
                        textAlignCenter()
                        width(ctx.pagerData.pageViewWidth)
                    }
                }

                // 3. 辅助说明文字
                View {
                    attr {
                        marginTop(40f)
                        paddingLeft(20f)
                        paddingRight(20f)
                    }
                    Text {
                        attr {
                            text("测试要点：")
                            fontSize(14f)
                            fontWeightBold()
                            marginBottom(8f)
                        }
                    }
                    Text {
                        attr {
                            text("1. 观察是否能够无限循环滑动（向左滑到第一张，向右滑到最后一张）。\n" +
                                 "2. 观察静止状态下是否能每隔 3 秒自动切换到下一页。\n" +
                                 "3. 观察IOS端手势滑动和自动轮播是否产生位移问题。")
                            fontSize(13f)
                            color(Color(0xFF666666L))
                            lineHeight(20f)
                        }
                    }
                }
            }
        }
    }

    private fun bannerImgClick(item: MockBannerItem) {
        println("[点击事件] 用户点击了 Banner: ${item.title}")
    }
}
