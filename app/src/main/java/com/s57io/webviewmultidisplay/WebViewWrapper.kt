package com.s57io.webviewmultidisplay

import android.content.Context
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*

class WebViewWrapper() {

    interface WebViewWrapperDelegate {
        fun showWindow(view : WebView, delegate : WebViewWrapperActionDelegate, viewId : Long)
        fun showFullscreen(view : View, delegate : WebViewWrapperActionDelegate, viewId : Long)
        fun setWebViewVisibility(viewId : Long, visibility: Int)
        fun onCloseFullscreen(viewId : Long)
        fun onPageFinished(view: WebView?, url: String?, viewId : Long) {}
        fun addJSInterface(webView: WebView) {}
    }

    interface WebViewWrapperActionDelegate {
        fun closeAction()
    }

    private class WebViewClientWrapper(delegate : WebViewWrapper.WebViewWrapperDelegate, viewId : Long) : WebViewClient() {
        private val delegate = delegate
        private val viewId = viewId
        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.proceed()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d(Constants.LOG_TAG, "onPageFinished($viewId): $url")
            delegate.onPageFinished(view, url, viewId)
            // Your code here
        }
    }

    class WebChromeClientWrapper(context : Context, delegate : WebViewWrapper.WebViewWrapperDelegate, viewId : Long) : WebChromeClient(), WebViewWrapperActionDelegate {
        private val context = context.applicationContext
        private val delegate = delegate
        private val viewId = viewId
        private var callback : WebChromeClient.CustomViewCallback? = null
        private var isFullscreen = false

        init {
            Log.d(Constants.LOG_TAG, "Create WebChromeClientWrapper ($viewId)")
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                request!!.resources.forEach { Log.d(Constants.LOG_TAG, "PermissionRequest: "+it) }
                //request!!.deny()
                request?.grant(request!!.resources)
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            // The title may contain the URL if the new window or pop-up is just navigating to a URL.
            Log.d(Constants.LOG_TAG, "onReceivedTitle(" + viewId + "): $title")
        }
        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            Log.d(Constants.LOG_TAG, "onCreateWindow(" + viewId + ")")

            val webViewWrapper : WebViewWrapper = WebViewWrapper()
            val newWebView = webViewWrapper.createWebView(context, delegate)

            val transport : WebView.WebViewTransport = resultMsg?.obj as WebView.WebViewTransport
            transport.webView = newWebView
            resultMsg?.sendToTarget()

            delegate.showWindow(newWebView,newWebView.webChromeClientWrapper, newWebView.viewId)
            return true
        }

        override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
            Log.d(Constants.LOG_TAG, "onShowCustomView() invoked")
            super.onShowCustomView(view, callback)

            isFullscreen = true
            this.callback = callback
            delegate.setWebViewVisibility(viewId, View.GONE)
            delegate.showFullscreen(view!!, this, -viewId)
        }

        override fun onHideCustomView() {
            super.onHideCustomView()
            isFullscreen = false
            Log.d(Constants.LOG_TAG, "onHideCustomView(" + viewId + ")")
            delegate.onCloseFullscreen(-viewId)
            delegate.setWebViewVisibility(viewId, View.VISIBLE)
        }

        override fun onCloseWindow(window: WebView?) {
            super.onCloseWindow(window)
            Log.d(Constants.LOG_TAG, "onCloseWindow(" + viewId + ")")
        }

        // Implementing WebViewWrapperActionDelegate
        override fun closeAction() {
            if (isFullscreen) {
                Log.d(Constants.LOG_TAG, "Closing fullscreen viewId = " + viewId)
                callback?.onCustomViewHidden()
                delegate.setWebViewVisibility(viewId, View.VISIBLE)
            }
        }

    }

    class CustomWebView(context: Context, delegate: WebViewWrapper.WebViewWrapperDelegate) : WebView(context) {
        val viewId: Long = System.currentTimeMillis()
        val webChromeClientWrapper = WebViewWrapper.WebChromeClientWrapper(context, delegate, viewId)

        init {
            Log.d(Constants.LOG_TAG, "Create WebView viewId = $viewId")
            webViewClient = WebViewClientWrapper(delegate, viewId)
            webChromeClient = webChromeClientWrapper
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            delegate.addJSInterface(this)
            configureSettings()
        }

        private fun configureSettings() {
            settings.apply {
                javaScriptEnabled = true
                setSupportZoom(true)
                mediaPlaybackRequiresUserGesture = false
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                displayZoomControls = false
                builtInZoomControls = false
            }
        }
    }

    fun createWebView(context: Context, delegate : WebViewWrapper.WebViewWrapperDelegate) : CustomWebView {
        return CustomWebView(context, delegate)
    }

}
