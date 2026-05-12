package com.wwwcg.kuikly.widgetgrid.adapter

import android.content.Context
import android.view.View
import com.tencent.kuikly.core.render.android.adapter.IKRPAGViewAdapter
import com.tencent.kuikly.core.render.android.adapter.IPAGView
import com.tencent.kuikly.core.render.android.adapter.IPAGViewListener
import org.libpag.PAGImage
import org.libpag.PAGImageLayer
import org.libpag.PAGTextLayer
import org.libpag.PAGView

/**
 * Created by kam on 2023/5/17.
 */
class PAGViewAdapter : IKRPAGViewAdapter {

    init {
        try {
            System.loadLibrary("pag")
        } catch (e: Throwable) {

        }
    }

    override fun createPAGView(context: Context): IPAGView {
        return KRPagView(context)
    }
}

class KRPagView(context: Context) : PAGView(context), IPAGView {

    private var listeners = mutableListOf<KRPagViewListener>()

    override fun asView(): View {
        return this
    }

    override fun setFilePath(filePath: String) {
        path = filePath
    }

    override fun setPAGViewRepeatCount(count: Int) {
        setRepeatCount(count)
    }

    override fun setPAGViewScaleMode(scaleMode: Int) {
        setScaleMode(scaleMode)
    }

    override fun playPAGView() {
        play()
    }

    override fun stopPAGView() {
        stop()
    }

    override fun setProgressPAGView(value: Double) {
        setProgress(value)
    }

    override fun replaceTextLayerContent(layerName: String, text: String) {
        if (composition == null) return
        val layers = composition.getLayersByName(layerName) ?: return
        layers.filterIsInstance<PAGTextLayer>().forEach {
            it.setText(text)
        }
    }

    override fun replaceImageLayerContent(layerName: String, filePath: String) {
        if (composition == null) return
        val layers = composition.getLayersByName(layerName) ?: return
        layers.filterIsInstance<PAGImageLayer>().forEach {
            if (filePath.startsWith("assets://")) {
                val assetManager = this.context.assets
                val image = PAGImage.FromAssets(assetManager, filePath.substring(9))
                it.setImage(image)
            } else {
                val image = PAGImage.FromPath(filePath)
                it.setImage(image)
            }
        }
    }

    override fun addPAGViewListener(listener: IPAGViewListener) {
        addListener(KRPagViewListener(listener).apply { listeners.add(this) })
    }

    override fun removePAGViewListener(listener: IPAGViewListener) {
        for (pagViewListener in listeners) {
            if (pagViewListener.listener == listener) {
                removeListener(pagViewListener)
                break
            }
        }
    }
}

class KRPagViewListener(val listener: IPAGViewListener) : PAGView.PAGViewListener {
    override fun onAnimationStart(view: PAGView) {
        listener.onAnimationStart(view)
    }

    override fun onAnimationEnd(view: PAGView) {
        listener.onAnimationEnd(view)
    }

    override fun onAnimationCancel(view: PAGView) {
        listener.onAnimationCancel(view)
    }

    override fun onAnimationRepeat(view: PAGView) {
        listener.onAnimationRepeat(view)
    }

    override fun onAnimationUpdate(view: PAGView) {
    }

}