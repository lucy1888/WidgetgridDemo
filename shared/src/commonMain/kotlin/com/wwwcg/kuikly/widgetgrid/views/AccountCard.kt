package cn.com.hzb.mobilebank.per.kuikly.views

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.layoutFrameDidChange
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.compose.SliderPage

internal class AccountCardView: ComposeView<AccountCardViewAttr, AccountCardViewEvent>() {
    private var cardWidth by observable(0f)
    override fun createEvent(): AccountCardViewEvent {
        return AccountCardViewEvent()
    }

    override fun createAttr(): AccountCardViewAttr {
        return AccountCardViewAttr()
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
                        attr{
                            width(ctx.cardWidth)
                            height(ctx.cardWidth * 0.69f)
                        }
                        Image {
                            attr {
                                src("assets://common/account.png")
                                positionAbsolute()
                                left(0f)
                                top(0f)
                                width(ctx.cardWidth)
                                height(ctx.cardWidth * 0.69f)
                            }
                        }
                        Image {
                            attr{
                                src("assets://common/detail.png")
                                positionAbsolute()
                                right(12f)
                                top(25f)
                                width(40f)
                                height(40f)
                            }
                        }
                        View {
                            attr{
                                flexDirectionRow()
                                marginLeft(20f)
                                marginTop(43f)
                            }
                            Text {
                                attr {
                                    color(Color.WHITE)
                                    text("当前余额(元)")
                                    fontSize(14f)
                                }
                            }
                            Image {
                                attr {
                                    src("assets://common/open.png")
                                    width(16f)
                                    height(16f)
                                    marginLeft(4f)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


internal class AccountCardViewAttr : ComposeAttr() {

}

internal class AccountCardViewEvent : ComposeEvent() {
    
}

internal fun ViewContainer<*, *>.AccountCard(init: AccountCardView.() -> Unit) {
    addChild(AccountCardView(), init)
}