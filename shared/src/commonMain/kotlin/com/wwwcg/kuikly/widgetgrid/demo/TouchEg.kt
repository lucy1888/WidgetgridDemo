package com.wwwcg.kuikly.widgetgrid.demo

import com.wwwcg.kuikly.widgetgrid.views.TouchDebugPanel
import com.wwwcg.kuikly.widgetgrid.base.BasePager
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder

@Page("TouchEg")
internal class TouchEg : BasePager() {
    
    override fun body(): ViewBuilder {
        val ctx = this
        return {
            TouchDebugPanel {

            }
        }
    }
}