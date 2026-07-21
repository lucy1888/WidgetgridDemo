package com.wwwcg.kuikly.widgetgrid.views

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.event.EventHandlerFn
import com.tencent.kuikly.core.global.GlobalFunctionRef
import com.tencent.kuikly.core.global.GlobalFunctions
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.views.PageList
import com.tencent.kuikly.core.views.PageListEvent
import com.tencent.kuikly.core.views.PageListView
import com.tencent.kuikly.core.views.ScrollParams

/**
 * PeekBanner 轮播图组件
 */
class PeekBannerView : ComposeView<PeekBannerAttr, PeekBannerEvent>() {

    lateinit var pageListRef: ViewRef<PageListView<*, *>>
    var currentPageIndex = 0
    private var timeoutTaskCallbackId: GlobalFunctionRef = ""
    private var isDragging = false
    private var isAutoPlaying = false
    private var isAnimating = false 
    private var viewDidLoad = false

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            PageList {
                ref {
                    ctx.pageListRef = it
                }
                attr {
                    if (ctx.attr.width > 0f) width(ctx.attr.width)
                    if (ctx.attr.height > 0f) height(ctx.attr.height)

                    scrollEnable(ctx.attr.scrollEnable)
                    pageItemWidth(ctx.attr.pageItemWidth)
                    pageItemHeight(ctx.attr.pageItemHeight)
                    
                    // 初始物理位置：跳过开头 2 个 Fake 项
                    defaultPageIndex(if (ctx.attr.itemCount > 1) 2 else ctx.attr.defaultPageIndex)
                    pageDirection(ctx.attr.isHorizontal)
                    showScrollerIndicator(false)
                    
                    overflow(false)
                    keepItemAlive = true
                }
                if (ctx.attr.lazyCreateItemsTask != null) {
                    apply(ctx.attr.lazyCreateItemsTask!!)
                }

                event {
                    scroll {
                        ctx.resetContentOffsetIfNeed(it)
                    }
                    pageIndexDidChanged {
                        ctx.isAnimating = false
                        ctx.firePageIndexDidChangedEvent(it as JSONObject)
                    }
                    dragBegin {
                        ctx.isDragging = true
                        ctx.stopLoopPlayIfNeed()
                    }
                    dragEnd {
                        ctx.isDragging = false
                        ctx.startLoopPlayIfNeed()
                    }
                }
            }
        }
    }

    override fun createAttr() = PeekBannerAttr()
    override fun createEvent() = PeekBannerEvent()

    override fun viewDidLoad() {
        super.viewDidLoad()
        viewDidLoad = true
        // 【关键修复】显式跳转到物理索引 2 (Real 0)
        // 使用 setTimeout 延迟 50ms 确保 PageList 的 Native 层已完成首帧布局并能响应滚动指令
        setTimeout(pagerId, 50) {
            if (attr.itemCount > 1) {
                // scrollToPage(0) 内部会映射到物理索引 2
                scrollToPage(0, false)
            }
        }
        startLoopPlayIfNeed()
    }

    override fun didRemoveFromParentView() {
        super.didRemoveFromParentView()
        stopLoopPlayIfNeed()
    }

    fun startLoopPlayIfNeed() {
        if (attr.itemCount > 1 && !isAutoPlaying && viewDidLoad) autoLoopPlay()
    }

    fun stopLoopPlayIfNeed() {
        if (timeoutTaskCallbackId.isNotEmpty()) {
            GlobalFunctions.destroyGlobalFunction(pagerId, timeoutTaskCallbackId)
            timeoutTaskCallbackId = ""
        }
        isAutoPlaying = false
    }

    private fun autoLoopPlay() {
        isAutoPlaying = true
        if (attr.loopPlayIntervalTimeMs <= 0) {
            stopLoopPlayIfNeed()
        } else {
            timeoutTaskCallbackId = setTimeout(pagerId, attr.loopPlayIntervalTimeMs) {
                if (attr.loopPlayIntervalTimeMs > 0) {
                    scrollToNextPageIfNeed()
                    autoLoopPlay()
                } else {
                    isAutoPlaying = false
                }
            }
        }
    }

    fun scrollToPage(index: Int, animation: Boolean = false) {
        val pageListView = pageListRef.view ?: return

        // 确保 renderView 已就绪，且 item 大小有效
        if (pageListView.renderView != null && !isDragging) {
            isAnimating = animation
            // 物理索引为 index + 2 (跳过开头的 2 个 Fake 项)
            // 参考 SliderPageView: 当滚动到最后一项跳转点时，增加微小偏移量 0.1f 确保触发 resetContentOffsetIfNeed 阈值
            var boundaryOffset = 0f
            if (index == attr.itemCount) {
                boundaryOffset = 0.1f
            }

            if (animation) {
                // 使用 setContentOffset 以便精确控制偏移量，确保在 iOS 端能触发滚动边界检查
                val targetX = (index + 2) * attr.pageItemWidth + boundaryOffset
                pageListView.setContentOffset(targetX, 0f, true)
            } else {
                pageListView.scrollToPageIndex(index + 2, false)
            }
        }
    }

    private fun scrollToNextPageIfNeed() = scrollToPage(currentPageIndex + 1, true)

    /**
     * 实时拦截滚动，处理无限循环重置
     * 优化思路：参考 SliderPageView，使用更严格的边界判断，解决 iOS 端位移补偿不准确问题
     */
    private fun resetContentOffsetIfNeed(scrollParams: ScrollParams) {
        if (attr.itemCount <= 1) return

        val offsetX = scrollParams.offsetX
        val itemWidth = attr.pageItemWidth
        if (itemWidth <= 0f) return

        // 真实数据的总宽度 (N * itemWidth)
        val realMoveWidth = attr.itemCount * itemWidth

        // 1. 向左滑到边缘：当滑到第一个 Fake 项区域 (物理索引 1 附近)
        // 阈值设为 itemWidth + 0.1f，确保在物理索引 2 (Real 0) 之前的项被检测到
        if (offsetX <= itemWidth + 0.1f) {
            pageListRef.view?.setContentOffset(offsetX + realMoveWidth, 0f)
        }
        // 2. 向右滑到边缘：当滑到末尾 Fake 项区域 (物理索引 N+2 附近)
        // 阈值设为 (N+2) * itemWidth - 1f，模仿 SliderPageView 的 offsetX + 1 >= contentWidth - viewWidth 逻辑
        else if (offsetX + 1f >= (attr.itemCount + 2) * itemWidth) {
            pageListRef.view?.setContentOffset(offsetX - realMoveWidth, 0f)
        }
    }

    /**
     * 处理索引变化事件，映射物理索引到逻辑索引
     */
    private fun firePageIndexDidChangedEvent(data: JSONObject) {
        val physicalIndex = data.optInt("index")
        if (attr.itemCount <= 0) return

        // 逻辑索引 = 物理索引 - 2 (跳过开头的 2 个 Fake 项)
        var logicalIndex = physicalIndex - 2

        if (attr.itemCount > 1) {
            if (logicalIndex < 0) {
                // 处理极左越界 (Index 0 -> Real Last)
                logicalIndex = attr.itemCount - 1
            } else if (logicalIndex >= attr.itemCount) {
                // 处理右侧 Fake 项 (Index N+1, N+2 -> Real 0, Real 1)
                logicalIndex %= attr.itemCount
            }
        }

        if (currentPageIndex != logicalIndex) {
            currentPageIndex = logicalIndex
            data.put("index", logicalIndex)
            event.onFireEvent(PageListEvent.PageListEventConst.PAGE_INDEX_DID_CHANGED, data)
        }
    }
}

class PeekBannerAttr : ComposeAttr() {
    var width: Float = 0f
    var height: Float = 0f
    var defaultPageIndex: Int = 0
    var isHorizontal: Boolean = true
    var pageItemWidth = 0f
    var pageItemHeight = 0f
    var scrollEnable: Boolean = true
    var loopPlayIntervalTimeMs: Int = 3000
        set(value) {
            if (value != field) {
                field = value
                (view() as? PeekBannerView)?.startLoopPlayIfNeed()
            }
        }

    internal var itemCount = 0
    internal var lazyCreateItemsTask: (PageListView<*, *>.() -> Unit)? = null

    fun <T> initSliderItems(dataList: List<T>, creator: PeekBannerItemCreator<T>) {
        if (dataList.isEmpty()) return
        itemCount = dataList.size
        val ctx = this
        lazyCreateItemsTask = {
            if (ctx.itemCount > 1) {
                // 1. 开头放 2 个 Fake 项：[Last-1], [Last]
                creator(dataList[(dataList.size - 2 + dataList.size) % dataList.size], dataList.last())
                creator(dataList.last(), dataList.first())

                // 2. 中间放置所有真实数据 (物理索引从 2 开始)
                for (i in dataList.indices) {
                    creator(dataList[i], dataList[(i + 1) % dataList.size])
                }

                // 3. 末尾放 3 个 Fake 项：[0], [1], [2]
                creator(dataList.first(), dataList[1 % dataList.size])
                creator(dataList[1 % dataList.size], dataList[2 % dataList.size])
                creator(dataList[2 % dataList.size], dataList[3 % dataList.size])
            } else {
                creator(dataList[0], dataList[0])
            }
        }
    }
}

class PeekBannerEvent : ComposeEvent() {
    fun pageIndexDidChanged(handler: EventHandlerFn) {
        register(PageListEvent.PageListEventConst.PAGE_INDEX_DID_CHANGED, handler)
    }
}

typealias PeekBannerItemCreator<T> = PageListView<*, *>.(currentItem: T, nextItem: T) -> Unit

fun ViewContainer<*, *>.PeekBanner(init: PeekBannerView.() -> Unit) {
    addChild(PeekBannerView(), init)
}
