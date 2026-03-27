package com.wwwcg.kuikly.widgetgrid.demo

import cn.com.hzb.mobilebank.per.kuikly.views.AccountCard
import cn.com.hzb.mobilebank.per.kuikly.views.ExpensesCard
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.views.ScrollerView

import com.wwwcg.kuikly.widgetgrid.base.BasePager

// ==================== 业务自定义卡片数据 ====================
/**
 * 账户明细
 */
class AccountCardData(scope: PagerScope) : WidgetGridItemData(scope){
    init {
        spanX = 2
        id = 1
    }
}
/**
 * 收支卡片
 */
class ExpensesCardData(scope: PagerScope) : WidgetGridItemData(scope)
{
    init {
        spanX = 2
        id = 2
    }
}

// ==================== Demo 页面 ====================

@Page("WidgetGridDemoPage")
internal class WidgetGridDemoPage : BasePager() {

    // 编辑状态
    var isEditing by observable(false)

    // WidgetGrid 引用
    lateinit var gridRef: ViewRef<WidgetGridView>
    lateinit var scrollerRef: ViewRef<ScrollerView<*, *>>

    // 用于生成新卡片 id
    private var nextId = 11

    // 网格配置
    private var gridConfig = WidgetGridConfig(
        dragOpacity = 1.0f,
        shakeInterval = 100,
        columnCount = 2,
        cardSpacing = 15f,
        shakeAngleBase = 5f,
        dragScaleRatio = 1.0f,
        deleteButtonOffset = -15f,
        deleteButtonSize = 48f,
        dragAnimationDuration = 0.18f
    )

    private val horizontalPadding = 16f

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFF1C1C1EL))
            }
            // 顶部导航栏
            View {
                attr {
                    paddingTop(ctx.pagerData.statusBarHeight)
                    backgroundColor(Color(0xFF2C2C2EL))
                }
                View {
                    attr {
                        height(56f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(16f)
                        paddingRight(16f)
                    }

                    // 返回按钮
                    View {
                        attr {
                            size(32f, 32f)
                            allCenter()
                        }
                        event {
                            click {
                                ctx.getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                            }
                        }
                        Text {
                            attr {
                                text("←")
                                fontSize(20f)
                                color(Color.WHITE)
                            }
                        }
                    }

                    // 标题
                    Text {
                        attr {
                            flex(1f)
                            text("小组件")
                            fontSize(18f)
                            fontWeightBold()
                            color(Color.WHITE)
                            textAlignCenter()
                        }
                    }

                    // 编辑/完成按钮
                    View {
                        attr {
                            paddingLeft(12f)
                            paddingRight(12f)
                            paddingTop(6f)
                            paddingBottom(6f)
                            backgroundColor(if (ctx.isEditing) Color(0xFF0A84FFL) else Color(0xFF3A3A3CL))
                            borderRadius(16f)
                        }
                        event {
                            click {
                                ctx.isEditing = !ctx.isEditing
                                ctx.gridRef.view?.setEditing(ctx.isEditing)
                            }
                        }
                        Text {
                            attr {
                                text(if (ctx.isEditing) "完成" else "编辑")
                                fontSize(14f)
                                color(Color.WHITE)
                            }
                        }
                    }
                }
            }

            // 卡片网格区域
            Scroller {
                attr {
                    flex(1f)
                    paddingLeft(ctx.horizontalPadding)
                    paddingRight(ctx.horizontalPadding)
                    paddingTop(16f)
                    paddingBottom(100f)
                }
                ref { ctx.scrollerRef = it }
                event{
                    register("longPress") {
                        KLog.d(
                            "WidgetGridDemoPage",
                            "Scroller 检测到长按！"
                        )
                        if (!ctx.isEditing) {
                            ctx.isEditing = true
                        }
                    }
                }
                // WidgetGrid 组件
                ctx.gridConfig = ctx.gridConfig.copy(cardHeight = 0.66f*(ctx.pagerData.pageViewWidth - ctx.horizontalPadding * 2))
                WidgetGrid {
                    ref {
                        ctx.gridRef = it
                    }

                    attr {
                        config = ctx.gridConfig
                        gridWidth = pagerData.pageViewWidth - ctx.horizontalPadding * 2
                        editing = ctx.isEditing

                        // 自定义卡片内容
                        cardContent { item ->
                            when (item) {
                                is AccountCardData -> {
                                    // 渲染账户卡片布局
                                    AccountCard {

                                    }
                                }

                                is ExpensesCardData -> {
                                    // 渲染收支卡片布局
                                    ExpensesCard {

                                    }
                                }

                            }
                        }
                    }

                    event {
                        onEditingChanged { editing ->
                            ctx.isEditing = editing
                        }
                        onReorder { from, to ->
                            // 可在此处理排序后的业务逻辑（如持久化）
                        }
                        onDelete { item ->
                            // 可在此处理删除后的业务逻辑
                        }
                        onDragStateChanged { dragging ->
                            ctx.scrollerRef.view?.getViewAttr()?.scrollEnable(!dragging)
                        }

                    }
                }
            }

        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        val items = mutableListOf<WidgetGridItemData>()
        items.add(AccountCardData(this))
        items.add(ExpensesCardData(this))
        gridRef.view?.addItems(items)
    }


}
