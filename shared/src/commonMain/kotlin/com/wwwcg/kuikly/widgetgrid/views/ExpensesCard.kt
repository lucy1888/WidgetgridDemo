package cn.com.hzb.mobilebank.per.kuikly.views

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.base.event.layoutFrameDidChange
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.ScrollPicker
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.compose.SliderPage

internal class ExpensesCardView: ComposeView<ExpensesCardViewAttr, ExpensesCardViewEvent>() {
    private var cardWidth by observable(0f)
    private var chooseIdx: Int by observable(0)
    private var chooseValue: String by observable("")
    override fun createEvent(): ExpensesCardViewEvent {
        return ExpensesCardViewEvent()
    }

    override fun createAttr(): ExpensesCardViewAttr {
        return ExpensesCardViewAttr()
    }

    override fun viewDidLayout() {
        super.viewDidLayout()

    }
    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                event{
                    layoutFrameDidChange {
                        ctx.cardWidth = it.width
                    }
                }
                vbind({ctx.cardWidth}) {
                    View {
                        Image {
                            attr {
                                src("assets://common/expense.png")
                                positionAbsolute()
                                left(0f)
                                top(0f)
                                width(ctx.cardWidth)
                                height(ctx.cardWidth * 0.69f)
                            }
                        }
                    }
                }
            }
        }
    }
}


internal class ExpensesCardViewAttr : ComposeAttr() {

}

internal class ExpensesCardViewEvent : ComposeEvent() {
    
}

internal fun ViewContainer<*, *>.ExpensesCard(init: ExpensesCardView.() -> Unit) {
    addChild(ExpensesCardView(), init)
}