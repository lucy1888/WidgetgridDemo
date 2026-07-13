package com.wwwcg.kuikly.widgetgrid.demo

import com.wwwcg.kuikly.widgetgrid.views.OpeningPeekBanner
import cn.com.hzb.mobilebank.per.kuikly.views.anniversaryHome.TripleTextBanner
import cn.com.hzb.mobilebank.per.kuikly.views.anniversaryHome.TripleBannerItem
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.directives.vif
import com.wwwcg.kuikly.widgetgrid.base.BasePager

/**
 * 开屏/顶部大图动画演示 Demo 页面
 * 目的：复现和对比手势上滑后，三端动画表现（容器高度、位置位移、组件内容缩放）不一致的现象。
 */
@Page("OpeningBannerAnimationDemoPage")
internal class OpeningBannerAnimationDemoPage : BasePager() {

    // 使用 key 机制强制刷新组件状态，方便反复测试
    var demoKey by observable(0L)

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFF5F5F5L))
            }

            // 1. 顶部标题栏
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
                            text("动画不一致现象 Demo")
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

                vif({ ctx.demoKey >= 0 }) {
                    OpeningPeekBanner {
                        // 逻辑已在内部实现，无需外部配置
                    }
                }

                // 3. TripleTextBanner 测试区域
                View {
                    attr {
                        marginTop(20f)
                    }
                    Text {
                        attr {
                            text("TripleTextBanner 组件测试")
                            fontSize(14f)
                            fontWeightBold()
                            marginBottom(12f)
                        }
                    }
                    vif({ ctx.demoKey >= 0 }) {
                        TripleTextBanner {
                            items.addAll(listOf(
                                TripleBannerItem(
                                    largeImageUrl = "",
                                    smallImageUrl = "",
                                    title = "理财产品优选A",
                                    longSubtitle = "年化收益稳健增长",
                                    shortSubtitle = "稳健增长",
                                    subtitleColor = "0xFF3B82F6",
                                    prdCatagory = "1",
                                    prdCode = "test_prd_001",
                                    rateValue = "3.85%",
                                    rateValueColor = "0xFFE85D67",
                                    rateName = "近一年年化",
                                    rateNameColor = "0xff4E5969",
                                    icon = "",
                                    bacJumplink = ""
                                ),
                                TripleBannerItem(
                                    largeImageUrl = "",
                                    smallImageUrl = "",
                                    title = "黄金积存计划B",
                                    longSubtitle = "避险资产配置优选",
                                    shortSubtitle = "避险优选",
                                    subtitleColor = "0xFFBC6F00",
                                    prdCatagory = "2",
                                    prdCode = "test_prd_002",
                                    rateValue = "¥568.50",
                                    rateValueColor = "0xFF1D2129",
                                    rateName = "当前金价",
                                    rateNameColor = "0xff4E5969",
                                    icon = "",
                                    bacJumplink = ""
                                ),
                                TripleBannerItem(
                                    largeImageUrl = "",
                                    smallImageUrl = "",
                                    title = "存款产品C",
                                    longSubtitle = "保本保息安全可靠",
                                    shortSubtitle = "安全可靠",
                                    subtitleColor = "0xFF22C55E",
                                    prdCatagory = "3",
                                    prdCode = "test_prd_003",
                                    rateValue = "2.10%",
                                    rateValueColor = "0xFF1D2129",
                                    rateName = "年利率",
                                    rateNameColor = "0xff4E5969",
                                    icon = "",
                                    bacJumplink = ""
                                ),
                                TripleBannerItem(
                                    largeImageUrl = "",
                                    smallImageUrl = "",
                                    title = "基金定投D",
                                    longSubtitle = "长期定投分散风险",
                                    shortSubtitle = "分散风险",
                                    subtitleColor = "0xFF8B5CF6",
                                    prdCatagory = "4",
                                    prdCode = "test_prd_004",
                                    rateValue = "+12.5%",
                                    rateValueColor = "0xFFE85D67",
                                    rateName = "近三月收益",
                                    rateNameColor = "0xff4E5969",
                                    icon = "",
                                    bacJumplink = ""
                                ),
                                TripleBannerItem(
                                    largeImageUrl = "",
                                    smallImageUrl = "",
                                    title = "保险保障E",
                                    longSubtitle = "全面守护家人健康",
                                    shortSubtitle = "全面守护",
                                    subtitleColor = "0xFF06B6D4",
                                    prdCatagory = "5",
                                    prdCode = "test_prd_005",
                                    rateValue = "¥100万",
                                    rateValueColor = "0xFF1D2129",
                                    rateName = "保障额度",
                                    rateNameColor = "0xff4E5969",
                                    icon = "",
                                    bacJumplink = ""
                                )
                            ))
                        }
                    }
                }

                // 4. 辅助说明文字
                View {
                    attr {
                        marginTop(20f)
                        paddingLeft(20f)
                        paddingRight(20f)
                    }
                    Text {
                        attr {
                            text("测试说明：")
                            fontSize(14f)
                            fontWeightBold()
                            marginBottom(8f)
                        }
                    }
                    Text {
                        attr {
                            text("1. 请尝试【向上滑动】顶部的图片区域，观察收起动画。\n" +
                                 "2. 观察图片（或PAG）淡出的同步率是否一致。\n" +
                                 "3. 容器高度变化与内容位移是否存在肉眼可见的“抖动”。")
                            fontSize(13f)
                            color(Color(0xFF666666L))
                            lineHeight(20f)
                        }
                    }
                }
            }
        }
    }
}
