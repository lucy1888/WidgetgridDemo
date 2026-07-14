package cn.com.hzb.mobilebank.per.kuikly.views.anniversaryHome


import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.timer.clearTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs

/**
 * TripleTextBanner 业务组件
 * 修复版：移除了 vforIndex 内部直接嵌套 vif 的非法方案，改用 opacity 控制隐藏，确保 UI 树结构稳定。
 */
class TripleTextBannerView : ComposeView<TripleTextBannerAttr, TripleTextBannerEvent>() {
    var items: ObservableList<TripleBannerItem> by observableList()
    private var currentIndex by observable(0)
    private var animDirection by observable(1) // 1=向前(next), -1=向后(prev)
    private var timerId: String = ""
    private var hasLoggedUiState = false
    // 曝光去重集合（按 prdCode 去重）
    private val exposedItems = mutableSetOf<String>()

    override fun createAttr() = TripleTextBannerAttr()
    override fun createEvent() = TripleTextBannerEvent()

    override fun created() {
        super.created()
        startAutoPlay()
        loadModuleConfigData()
    }


    override fun viewDestroyed() {
        stopAutoPlay()
        super.viewDestroyed()
    }

    private fun startAutoPlay() {
        if (!attr.autoPlay || attr.autoPlayInterval <= 0) return
        // 卡片数量小于等于3个时，不自动播放
        if (items.size <= 3) return
        stopAutoPlay()
        timerId = setTimeout(attr.autoPlayInterval.toInt()) {
            next()
            startAutoPlay()
        }
    }

    private fun stopAutoPlay() {
        if (timerId.isNotEmpty()) {
            clearTimeout(timerId)
            timerId = ""
        }
    }

    fun next() {
        val size = items.size
        // 卡片数量小于等于3个时，不滑动
        if (size <= 3 || size == 0) return
        animDirection = 1
        currentIndex = (currentIndex + 1) % size
    }

    fun prev() {
        val size = items.size
        // 卡片数量小于等于3个时，不滑动
        if (size <= 3 || size == 0) return
        animDirection = -1
        currentIndex = (currentIndex - 1 + size) % size
    }
    /**
     * 加载模块配置数据 - 调用 /celebration/moduleConfigQry 接口
     * 支持多级 tabList 嵌套结构
     */
    private fun loadModuleConfigData() {

    }

    /**
     * 递归解析 tab 节点，支持多级嵌套结构
     * @param tabObj tab 节点对象
     * @param bannerItems 轮播项列表（输出参数）
     */
    private fun parseTabNode(
        tabObj: JSONObject,
        bannerItems: MutableList<TripleBannerItem>
    ) {
        val tabLevel = tabObj.optString("tabLevel", "")

        // 只处理 tabLevel="2" 的节点作为轮播项
        if (tabLevel == "2") {
            val tabName = tabObj.optString("tabName", "")
            if (tabName.isNotBlank()) {
                // 从当前节点获取背景图信息
                val largeImage = tabObj.optString("tabBackground", "")
                val smallImage = tabObj.optString("tabBackground2", "")

                // 解析 subHeading2（大卡片副标题），格式："文本_颜色值"
                val subHeading2Raw = tabObj.optString("subHeading", "")
                val (longSubtitle, longSubtitleColor) = parseSubHeading2(subHeading2Raw)

                // 解析 subHeading（小卡片副标题），支持多颜色格式
                val subHeadingRaw = tabObj.optString("subHeading2", "")
                val (shortSubtitle, shortSubtitleFirstPart, shortSubtitleSecondPart, shortSubtitleFirstColor, shortSubtitleSecondColor) = parseSubHeadingForSmallCard(subHeadingRaw)

                // 解析 bannerRateValue 和 bannerRateName，格式："数值_颜色值" 和 "名称_颜色值"
                val bannerRateValueRaw = tabObj.optString("bannerRateValue", "")
                val (rateValue, rateValueColor) = parseSubHeading2(bannerRateValueRaw)
                val bannerRateNameRaw = tabObj.optString("bannerRateName", "")
                val (rateName, rateNameColor) = parseSubHeading2(bannerRateNameRaw)

                // 优先使用长副标题的颜色，如果没有则根据 type 映射颜色
                val subtitleColor = longSubtitleColor
                val firstProduct = tabObj.optJSONArray("productList")?.optJSONObject(0)
                val prdCatagory = tabObj.optString("prdCatagory", "").ifBlank {
                    firstProduct?.optString("prdCatagory", "") ?: ""
                }
                val prdCode = tabObj.optString("prodCode", "").ifBlank {
                    firstProduct?.optString("prodCode", "") ?: ""
                }
                val icon = tabObj.optString("icon", "")
                val bacJumplink = tabObj.optString("bacJumplink", "")
                // 构造 TripleBannerItem
                val bannerItem = TripleBannerItem(
                    largeImageUrl = largeImage,
                    smallImageUrl = smallImage,
                    title = tabName,
                    longSubtitle = longSubtitle,
                    shortSubtitle = shortSubtitle,
                    bottomDescript = null, // 二级节点没有 bottomDescript
                    subtitleColor = subtitleColor, // 保存副标题颜色
                    icon = icon,
                    prdCatagory = prdCatagory,
                    prdCode = prdCode,
                    rateValue = rateValue, // 产品净值数值
                    rateValueColor = rateValueColor, // 产品净值颜色
                    rateName = rateName, // 产品净值名称
                    rateNameColor = rateNameColor, // 产品净值名称颜色
                    bacJumplink = bacJumplink, // 模块跳转地址
                    shortSubtitleFirstPart = shortSubtitleFirstPart, // 小卡片副标题第一部分
                    shortSubtitleSecondPart = shortSubtitleSecondPart, // 小卡片副标题第二部分
                    shortSubtitleFirstColor = shortSubtitleFirstColor, // 小卡片副标题第一部分颜色
                    shortSubtitleSecondColor = shortSubtitleSecondColor // 小卡片副标题第二部分颜色
                )
                bannerItems.add(bannerItem)
                KLog.d("TripleTextBanner", "添加轮播项: title=${bannerItem.title}")
                KLog.d("TripleTextBanner", "  - largeImageUrl=$largeImage")
                KLog.d("TripleTextBanner", "  - smallImageUrl=$smallImage")
                KLog.d("TripleTextBanner", "  - longSubtitle=$longSubtitle, shortSubtitle=$shortSubtitle")
                KLog.d("TripleTextBanner", "  - subtitleColor=$subtitleColor")
                KLog.d("TripleTextBanner", "  - rateValue=$rateValue, rateValueColor=$rateValueColor")
                KLog.d("TripleTextBanner", "  - rateName=$rateName, rateNameColor=$rateNameColor")
                KLog.d("TripleTextBanner", "  - shortSubtitleFirstPart=$shortSubtitleFirstPart, shortSubtitleSecondPart=$shortSubtitleSecondPart")
            }
        }

        // 递归处理子级 tabList
        val tabList = tabObj.optJSONArray("tabList")
        if (tabList != null && tabList.length() > 0) {
            for (k in 0 until tabList.length()) {
                val childTabObj = tabList.optJSONObject(k)
                if (childTabObj != null) {
                    parseTabNode(childTabObj, bannerItems)
                }
            }
        }
    }

    /**
     * 解析 subHeading2 字段，格式："文本_颜色值"
     * @param raw 原始字符串，如 "收益天天见_0xFF3B82F6"
     * @return Pair(文本, 颜色字符串)，如果没有下划线则返回 (raw, "")
     */
    private fun parseSubHeading2(raw: String): Pair<String, String> {
        if (raw.isBlank()) return Pair("", "")

        val lastUnderscoreIndex = raw.lastIndexOf('_')
        return if (lastUnderscoreIndex > 0) {
            val text = raw.substring(0, lastUnderscoreIndex)
            val color = raw.substring(lastUnderscoreIndex + 1)
            Pair(text, color)
        } else {
            Pair(raw, "")
        }
    }

    /**
     * 解析小卡片副标题，支持多颜色格式
     * 格式1: "文本_颜色值" -> 返回 (完整文本, "", "", "", "")
     * 格式2: "数值_颜色值1文本_颜色值2" -> 拆分为两部分并提取颜色
     * @param raw 原始字符串，如 "0_0xFFBC6F00元/克_#808080"
     * @return 五元组(完整文本, 第一部分, 第二部分, 第一部分颜色, 第二部分颜色)
     */
    private fun parseSubHeadingForSmallCard(raw: String): Tuple5<String, String, String, String, String> {
        if (raw.isBlank()) return Tuple5("", "", "", "", "")

        // 尝试匹配多颜色格式："数值_颜色值1文本_颜色值2"
        // 例如："0_0xFFBC6F00元/克_#808080"
        val multiColorPattern = Regex("^([^_]+)_(0[xX][0-9A-Fa-f]{8}|#[0-9A-Fa-f]{6,8})(.+?)_(0[xX][0-9A-Fa-f]{8}|#[0-9A-Fa-f]{6,8})$")
        val match = multiColorPattern.find(raw)

        if (match != null) {
            val firstPart = match.groupValues[1]  // "0"
            val firstColor = match.groupValues[2]  // "0xFFBC6F00"
            val secondPart = match.groupValues[3]  // "元/克"
            val secondColor = match.groupValues[4]  // "#808080"
            val fullText = "$firstPart$secondPart"  // "0元/克"
            KLog.d("TripleTextBanner", "解析多颜色subHeading: $raw -> firstPart=$firstPart($firstColor), secondPart=$secondPart($secondColor)")
            return Tuple5(fullText, firstPart, secondPart, firstColor, secondColor)
        }

        // 单颜色格式或无颜色格式
        val lastUnderscoreIndex = raw.lastIndexOf('_')
        return if (lastUnderscoreIndex > 0) {
            val text = raw.substring(0, lastUnderscoreIndex)
            val color = raw.substring(lastUnderscoreIndex + 1)
            Tuple5(text, "", "", color, "")
        } else {
            Tuple5(raw, "", "", "", "")
        }
    }
    /**
     * 产品曝光埋点
     */
    private fun trackExposure(item: TripleBannerItem) {
        val resourceId = item.prdCode.ifEmpty { item.title }
        if (resourceId.isEmpty() || !exposedItems.add(resourceId)) return

    }

    /**
     * 产品点击埋点
     */
    private fun trackClick(item: TripleBannerItem) {

    }

    override fun body(): ViewBuilder {
        val ctx = this
        var startX = 0f
        var startY = 0f

        // 尺寸定义
        val largeW = 161f
        val largeH = 100f
        val smallW = 83f
        val smallH = 100f
        val gap = 8f
        val paddingLeft = 0f

        return {
            vif({ctx.items.isNotEmpty()}) {
                View {
                    attr {
                        height(largeH)
                        overflow(true)
                    }

                    // 监听 currentIndex 变化，触发当前产品曝光埋点
                    vbind({ ctx.currentIndex }) {
                        if (ctx.items.isNotEmpty() && ctx.currentIndex < ctx.items.size) {
                            ctx.trackExposure(ctx.items[ctx.currentIndex])
                        }
                    }

                    // 1. 渲染卡片
                    vforIndex({ ctx.items }) { item, index, _ ->
                        // 修复：vforIndex 内部必须直接是一个 View 节点
                        View {
                            attr {
                                positionType(FlexPositionType.ABSOLUTE)

                                val size = ctx.items.size
                                val diff =
                                    if (size > 0) (index - ctx.currentIndex + size) % size else 0

                                // 动画状态映射 - TripleTextBanner 只展示 3 张卡
                                // diff=0:大卡片  diff=1:小卡①  diff=2:小卡②
                                // diff>=3: 隐藏在右侧等待 / diff=size-1: 离场卡(alpha=0 向左/右滑出)
                                val (targetW, targetH, targetX, targetAlpha, targetZ) = when (diff) {
                                    0 -> Quint(largeW, largeH, paddingLeft, 1f, 10)
                                    1 -> Quint(smallW, smallH, paddingLeft + largeW + gap, 1f, 5)
                                    2 -> Quint(
                                        smallW,
                                        smallH,
                                        paddingLeft + largeW + gap + smallW + gap,
                                        1f,
                                        2
                                    )
                                    size - 1 -> {
                                        if (size == 4) {
                                            // 4项时 diff=3 是待进入项，方向决定它在左侧还是右侧等待
                                            if (ctx.animDirection > 0) {
                                                Quint(smallW, smallH, pagerData.pageViewWidth + smallW + gap, 0f, 0)
                                            } else {
                                                Quint(largeW, largeH, -largeW, 0f, 0)
                                            }
                                        } else {
                                            // 5+项时: forward 离场向左退出 / backward 入场从左侧进入
                                            Quint(largeW, largeH, -largeW, 0f, 0)
                                        }
                                    }
                                    else -> Quint(
                                        smallW, smallH,
                                        pagerData.pageViewWidth + smallW + gap,
                                        0f, 0
                                    )
                                }

                                if (!ctx.hasLoggedUiState) {
                                    KLog.d("TripleTextBanner-UI", "[$index] title=${item.title} curIdx=${ctx.currentIndex} diff=$diff " +
                                            "targetW=$targetW targetAlpha=$targetAlpha")
                                }

                                width(targetW)
                                height(targetH)
                                left(targetX)
                                top((largeH - targetH) / 2)
                                borderRadius(8f)

                                // 可见性: 始终只展示 3 张 (diff 0/1/2); size-1 离场态 alpha=0
                                val isVisible = diff == 0 || diff == 1 || diff == 2 || (diff == size - 1 && size >= 4)
                                visibility(isVisible)
                                opacity(targetAlpha)
                                if (!ctx.hasLoggedUiState) {
                                    KLog.d("TripleTextBanner-UI", "[$index] visible=$isVisible size=$size")
                                    ctx.hasLoggedUiState = true
                                }
                                if ((if (ctx.items.isNotEmpty()) (index - ctx.currentIndex + ctx.items.size) % ctx.items.size else 0) == 0) {
                                    backgroundImage(item.largeImageUrl) { resizeStretch() }
                                    backgroundColor(Color(0xFFD6E4FF))
                                } else {
                                    backgroundImage(item.smallImageUrl) { resizeStretch() }
                                    backgroundColor(Color(0xFFFFE8D6))
                                }
                                animate(Animation.easeInOut(0.5f), ctx.currentIndex)
                                touchEnable(true)
                            }
                            event {
                                touchDown { params ->
                                    startX = params.pageX
                                    startY = params.pageY
                                    ctx.stopAutoPlay()
                                }
                                touchUp { params ->
                                    val dx = params.pageX - startX
                                    val dy = params.pageY - startY
                                    // 卡片数量小于等于3个时，不响应滑动切换，只处理点击事件
                                    if (ctx.items.size > 3 && abs(dx) > abs(dy) && abs(dx) > 20f) {
                                        if (dx < 0) ctx.next() else ctx.prev()
                                    } else if (abs(dx) <= 20f && abs(dy) <= 20f) {
                                        // 点击埋点
                                        ctx.trackClick(item)
                                    }
                                    ctx.startAutoPlay()
                                }
                                touchCancel {
                                    ctx.startAutoPlay()
                                }
                            }

                            View {
                                val size = ctx.items.size
                                attr {
                                    padding(8f)
                                    flex(1f)
                                    alignSelfStretch()
                                    flexDirectionColumn()
                                }
                                Text {
                                    attr {
                                        text(item.title)
                                        color(Color(0xff1D2129))
                                        marginBottom(10f)
                                        fontWeightMedium()
                                        fontSize(if ((if (size > 0) (index - ctx.currentIndex + size) % size else 0) == 0) 14f else 12f)
                                        lines(2)
                                        textOverFlowTail()
                                        animate(Animation.easeInOut(0.5f), ctx.currentIndex)
                                    }
                                }
                                // 副标题：大卡片显示 longSubtitle，小卡片如果没有多颜色则显示 shortSubtitle
                                vif({ ((if (size > 0) (index - ctx.currentIndex + size) % size else 0) == 0) || item.shortSubtitleFirstPart.isEmpty() }) {
                                    Text {
                                        attr {
                                            // diff=0 时使用长副标题，其他情况使用短副标题
                                            val isLargeCard =
                                                (if (size > 0) (index - ctx.currentIndex + size) % size else 0) == 0
                                            val subtitle =
                                                if (isLargeCard) item.longSubtitle else item.shortSubtitle
                                            text(subtitle)
                                            fontWeightNormal()
                                            // 使用 subtitleColor，如果为空则使用默认颜色
                                            val subtitleColorValue =
                                                if (item.subtitleColor.isNotBlank()) {
                                                    parseSubtitleColor(item.subtitleColor) ?: Color(
                                                        0xFF3B82F6
                                                    )
                                                } else {
                                                    Color(0xFF3B82F6)
                                                }
                                            color(subtitleColorValue!!)
                                            fontSize(10f)
                                            animate(Animation.easeInOut(0.5f), ctx.currentIndex)
                                        }
                                    }
                                }

                                // 小卡片时，如果 shortSubtitle 包含多颜色，分段显示
                                vif({ item.shortSubtitleFirstPart.isNotEmpty() && (if (size > 0) (index - ctx.currentIndex + size) % size else 0) != 0 }) {
                                    val firstPartColor =
                                        if (item.shortSubtitleFirstColor.isNotBlank()) {
                                            parseSubtitleColor(item.shortSubtitleFirstColor)
                                                ?: Color(0xFF3B82F6)
                                        } else {
                                            Color(0xFF3B82F6)
                                        }
                                    val secondPartColor =
                                        if (item.shortSubtitleSecondColor.isNotBlank()) {
                                            parseSubtitleColor(item.shortSubtitleSecondColor)
                                                ?: Color(0xff4E5969)
                                        } else {
                                            Color(0xff4E5969)
                                        }
                                    View {
                                        attr {
                                            flexDirectionRow()
                                            alignItemsCenter()
                                            marginTop(4f)
                                            animate(Animation.easeInOut(0.5f), ctx.currentIndex)
                                        }
                                        Text {
                                            attr {
                                                text(item.shortSubtitleFirstPart)
                                                fontSize(10f)
                                                fontWeightNormal()
                                                color(firstPartColor)
                                                animate(Animation.easeInOut(0.5f), ctx.currentIndex)
                                            }
                                        }
                                        Text {
                                            attr {
                                                text(item.shortSubtitleSecondPart)
                                                fontSize(10f)
                                                marginLeft(2f)
                                                color(secondPartColor)
                                                animate(Animation.easeInOut(0.5f), ctx.currentIndex)
                                            }
                                        }
                                    }
                                }

                                vif({ item.rateValue.isNotEmpty() && (if (size > 0) (index - ctx.currentIndex + size) % size else 0) == 0 }) {
                                    KLog.d(
                                        "TripleTextBanner",
                                        "渲染 rateValue: ${item.rateValue}, rateName: ${item.rateName}, diff=0"
                                    )
                                    val rateColor = if (item.rateValueColor.isNotBlank()) {
                                        parseSubtitleColor(item.rateValueColor) ?: Color(0xff1D2129)
                                    } else {
                                        Color(0xff1D2129)
                                    }
                                    val nameColor = if (item.rateNameColor.isNotBlank()) {
                                        parseSubtitleColor(item.rateNameColor) ?: Color(0xff4E5969)
                                    } else {
                                        Color(0xff4E5969)
                                    }
                                    View {
                                        attr {
                                            alignItemsFlexStart()
                                            marginTop(12f)
                                            animate(Animation.easeInOut(0.5f), ctx.currentIndex)
                                        }
                                        Text {
                                            attr {
                                                text(item.rateValue)
                                                fontSize(16f)
                                                fontWeightBold()
                                                color(rateColor)
                                                animate(Animation.easeInOut(0.5f), ctx.currentIndex)
                                            }
                                        }
                                        Text {
                                            attr {
                                                text(item.rateName.ifEmpty { "" })
                                                fontSize(10f)
                                                color(nameColor)
                                                lineHeight(18f)
                                                animate(Animation.easeInOut(0.5f), ctx.currentIndex)
                                            }
                                        }
                                    }
                                }
                                velse {
                                    View {
                                        attr {
                                            positionType(FlexPositionType.ABSOLUTE)
                                            left(8f)
                                            bottom(12f)
                                            height(22f)
                                            width(54f)
                                            justifyContentCenter()
                                            alignItemsCenter()
                                            borderRadius(11f)
                                            // 大卡片才展示按钮，小卡片直接隐藏，不参与动画避免半透明闪动
                                            if ((if (size > 0) (index - ctx.currentIndex + size) % size else 0) == 0) {
                                                backgroundColor(Color.WHITE)
                                                visibility(true)
                                            } else {
                                                visibility(false)
                                            }
                                        }
                                        Text {
                                            attr {
                                                text("去看看")
                                                color(Color(0xff1D2129))
                                                fontSize(10f)
                                            }
                                        }
                                    }
                                }
                                View {
                                    attr {
                                        positionType(FlexPositionType.ABSOLUTE)
                                        right(0f)
                                        bottom(0f)
                                    }
                                    Image {
                                        attr {
                                            if ((if (size > 0) (index - ctx.currentIndex + size) % size else 0) == 0) {
                                                width(76f)
                                                height(76f)
                                            } else {
                                                width(66f)
                                                height(66f)
                                            }
                                            src(item.icon)
                                        }
                                    }

                                }
                            }
                        }
                    }

                }
            }
        }
    }
}

/**
 * 解析颜色字符串为 Color 对象
 * @param colorStr 颜色字符串，格式如 "0xFF3B82F6"、"#FF3B82F6" 或 "#808080"
 * @return Color 对象，解析失败返回 null
 */
private fun parseSubtitleColor(colorStr: String): Color? {
    if (colorStr.isBlank()) return null

    return try {
        var hexStr = colorStr.trim().removePrefix("0x").removePrefix("0X").removePrefix("#")

        // 如果只有6位颜色值（RGB），添加透明度前缀 ff（完全不透明）
        if (hexStr.length == 6) {
            hexStr = "ff$hexStr"
        }

        val colorValue = hexStr.toLongOrNull(16)
        if (colorValue != null) Color(colorValue) else null
    } catch (e: Exception) {
        KLog.e("TripleTextBanner", "解析颜色失败: $colorStr, error: ${e.message}")
        null
    }
}

private data class Quint<A, B, C, D, E>(val w: A, val h: B, val x: C, val alpha: D, val z: E)

/**
 * 五元组数据类
 */
data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

/**
 * TripleBannerItem 数据模型
 * @param largeImageUrl 大卡片背景图
 * @param smallImageUrl 小卡片背景图
 * @param longTitle 长描述文字（大卡片显示）
 * @param shortTitle 短描述文字（小卡片显示）
 * @param longSubtitle 长副标题（大卡片显示）
 * @param shortSubtitle 短副标题（小卡片显示）
 * @param subtitleColor 副标题颜色（可选，格式如 "0xFF3B82F6"）
 * @param rateValueColor 产品净值数值颜色（如 "0xFFE85D67"）
 * @param rateName 产品净值名称（如 "产品净值"）
 * @param rateNameColor 产品净值名称颜色（如 "#808080"）
 * @param shortSubtitleFirstPart 小卡片副标题第一部分（多颜色场景）
 * @param shortSubtitleSecondPart 小卡片副标题第二部分（多颜色场景）
 * @param shortSubtitleFirstColor 小卡片副标题第一部分颜色（如 "0xFFBC6F00"）
 * @param shortSubtitleSecondColor 小卡片副标题第二部分颜色（如 "#808080"）
 */
data class TripleBannerItem(
    val largeImageUrl: String,
    var icon:String,
    val smallImageUrl: String,
    val title: String,
    val longSubtitle: String = "",
    val shortSubtitle: String = "",
    val bottomDescript: String? = null,
    val subtitleColor: String = "",
    val prdCatagory: String = "",
    val prdCode: String = "",
    val rateValue: String = "",
    val rateValueColor: String = "",
    val rateName: String = "",
    val rateNameColor: String = "",
    val bacJumplink: String = "",
    val shortSubtitleFirstPart: String = "",
    val shortSubtitleSecondPart: String = "",
    val shortSubtitleFirstColor: String = "",
    val shortSubtitleSecondColor: String = ""
)

class TripleTextBannerAttr : ComposeAttr() {
    var autoPlay: Boolean = true
    var autoPlayInterval: Long = 3000L
}
class TripleTextBannerEvent : ComposeEvent()
fun ViewContainer<*, *>.TripleTextBanner(init: TripleTextBannerView.() -> Unit) {
    addChild(TripleTextBannerView(), init)
}
