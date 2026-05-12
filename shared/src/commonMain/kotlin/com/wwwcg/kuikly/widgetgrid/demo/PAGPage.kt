package com.wwwcg.kuikly.widgetgrid.demo

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.PAG
@Page("PAGPage")
class PAGPage: Pager() {
    override fun body(): ViewBuilder {
        return {
            attr {
                allCenter()
            }
            PAG {
                attr {
                    src(ImageUri.commonAssets("blue.pag"))
                    repeatCount(0)
                    autoPlay(true)
                    setTimeout(100) {
                        replaceTextLayerContent(
                            "text01",
                            "123"
                        )
                    }
                    replaceTextLayerContent(
                        "text02",
                        "456"
                    )
                    size(
                        80f,
                        80f
                    )
                }
                event {
                    click {
                        KLog.e("PAG","pag click")
                    }
                }
            }
        }
    }
}