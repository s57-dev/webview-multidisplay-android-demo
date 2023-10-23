/*
 * Copyright 2023 S57 ApS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.s57io.webviewmultidisplay

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*

/**
 * `WebViewWrapper` manages WebViews with customization options and event handling,
 * exposed via `WebViewWrapperDelegate` and `WebViewWrapperActionDelegate`.
 *
 * It encapsulates WebView creation, configuration, and event handling, facilitating
 * easy integration and management in an Android application, ensuring seamless user
 * interaction with web content.
 *
 * This class handles window pop-ups, full screen requests, visibility changes, SSL errors,
 * and more, allowing the host application to inject native objects into the WebView
 * instances, extending functionality and enabling rich web-native interactions.
 *
 * The host application should implement `WebViewWrapperDelegate` to receive notifications
 * on WebView window pop-up, fullscreen request, etc.
 */
class WebViewWrapper {

    /**
     * Delegate interface through which the host application gets notified from the WebView.
     * It defined methods for managing window pop-up, fullscreen requests, visibility and
     * for injecting Javascript bindings.
     */
    interface WebViewWrapperDelegate {
        /**
         * Called when a new WebView instance needs to be presented as a result of
         * a window pop-up initiated by window.open() from the web domain.
         *
         * @param view The new WebView instance to present.
         * @param delegate Reference to WebViewWrapperActionDelegate object,
         *                 where closeAction must be called by the host when
         *                 the window or WebView is to be closed, e.g., by user action.
         * @param viewId Unique ID for the WebView instance.
         */
        fun showWindow(view: WebView, delegate: WebViewWrapperActionDelegate, viewId: Long)

        /**
         * Called when a request to enter full screen mode is initiated by requestFullscreen()
         * from the web domain..
         *
         * @param view The view to present in full screen.
         * @param delegate Reference to WebViewWrapperActionDelegate object,
         *                 where closeAction must be called by the host when
         *                 full screen mode is to be exited, e.g., by user action.
         * @param viewId Unique ID for the full screen view.
         */
        fun showFullscreen(view: View, delegate: WebViewWrapperActionDelegate, viewId: Long)

        /**
         * Called to change the visibility of a WebView window when a full screen request is initiated.
         * This method is called with View.GONE immediately after showFullscreen() to indicate that the
         * parent WebView should be hidden. Similarly, it is called with View.VISIBLE immediately after
         * onCloseFullscreen() to indicate that the parent WebView should be visible again.
         *
         * @param viewId Unique ID for the WebView instance.
         * @param visibility The desired visibility state. Possible values are View.GONE and View.VISIBLE.
         */
        fun setWebViewVisibility(viewId: Long, visibility: Int) {}

        /**
         * Called when the full screen view is exited.
         *
         * @param viewId Unique ID for the full screen view.
         */
        fun onCloseFullscreen(viewId: Long)

        /**
         * Called when a web page has finished loading in a WebView instance.
         *
         * @param view The WebView instance where the page has finished loading.
         * @param url The URL of the loaded page.
         * @param viewId Unique ID for the WebView instance.
         */
        fun onPageFinished(view: WebView?, url: String?, viewId: Long) {}

        /**
         * Called when a new WebView instance is created. Allows the host to add a
         * JavaScript interface for injecting native objects into the WebView instance.
         *
         * @param webView The WebView instance.
         */
        fun addJSInterface(webView: WebView) {}
    }

    /**
     * Delegate interface through which the host application performs actions related
     * to closing views etc.
     */
    interface WebViewWrapperActionDelegate {
        /**
         * Called the host application when closing a full screen view, e.g., by user action.
         */
        fun closeAction()
    }

    /**
     * This class implements the WebViewClient interfaces currently required for the
     * CustomWebView class.
     *
     * @param delegate reference to the object implementing WebViewWrapperDelegate interface.
     * @param viewId Unique ID for the WebView instance.
     */
    private class WebViewClientWrapper(private val delegate: WebViewWrapperDelegate,
                                       private val viewId: Long
    ) : WebViewClient() {
        /**
         * Accept SSL certificate.
         * Warning: Do not use this in production code. Accepting all certificates is insecure.
         *
         * @param view WebView instance
         * @param handler SslErrorHandler instance
         * @param error SslError instance
         */
        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            Log.d(Constants.LOG_TAG, "onReceivedSslError($viewId): $error")
            if (Constants.ACCEPT_SELF_SIGNED_CERTIFICATES) {
                handler?.proceed()
            } else {
                handler?.cancel()
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d(Constants.LOG_TAG, "onPageFinished($viewId): $url")
            delegate.onPageFinished(view, url, viewId)
        }
    }

    /**
     * This class implements the WebChromeClient interfaces currently required for the
     * CustomWebView class. It also implements the WebViewWrapperActionDelegate interface
     * for managing fullscreen view closing.
     *
     * It implements the logic for managing the communication with the host via the
     * WebViewWrapperDelegate interface.
     *
     * @param context reference to the context where the WebView is instantiated.
     * @param delegate reference to the object implementing WebViewWrapperDelegate interface.
     * @param viewId Unique ID for the WebView instance.
     */
    class WebChromeClientWrapper(context : Context,
                                 private val delegate: WebViewWrapperDelegate,
                                 private val viewId: Long
    ) : WebChromeClient(), WebViewWrapperActionDelegate {
        private val context = context.applicationContext
        private var callback : CustomViewCallback? = null
        private var isFullscreen = false

        init {
            Log.d(Constants.LOG_TAG, "Create WebChromeClientWrapper ($viewId)")
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            request!!.resources.forEach { Log.d(Constants.LOG_TAG, "PermissionRequest: $it") }
            //request!!.deny()
            request.grant(request.resources)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            // The title may contain the URL if the new window or pop-up is just navigating to a URL.
            Log.d(Constants.LOG_TAG, "onReceivedTitle($viewId): $title")
        }

        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            Log.d(Constants.LOG_TAG, "onCreateWindow($viewId)")

            val webViewWrapper = WebViewWrapper()
            val newWebView = webViewWrapper.createWebView(context, delegate)

            val transport : WebView.WebViewTransport = resultMsg?.obj as WebView.WebViewTransport
            transport.webView = newWebView
            resultMsg.sendToTarget()

            delegate.showWindow(newWebView,newWebView.webChromeClientWrapper, newWebView.viewId)
            return true
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
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
            Log.d(Constants.LOG_TAG, "onHideCustomView($viewId)")
            delegate.onCloseFullscreen(-viewId)
            delegate.setWebViewVisibility(viewId, View.VISIBLE)
        }

        override fun onCloseWindow(window: WebView?) {
            super.onCloseWindow(window)
            Log.d(Constants.LOG_TAG, "onCloseWindow($viewId)")
        }

        // WebViewWrapperActionDelegate inmplementation
        override fun closeAction() {
            if (isFullscreen) {
                Log.d(Constants.LOG_TAG, "Closing fullscreen viewId = $viewId")
                callback?.onCustomViewHidden()
                delegate.setWebViewVisibility(viewId, View.VISIBLE)
            }
        }

    }

    /**
     * This class is responsible for creating the actual WebView and for setting the
     * the WebViewClient (WebViewClientWrapper) and WebChromeClient
     * (WebChromeClientWrapper) implementations on the WebView instance. It also
     * configures the default setting WebView settings in configureSettings.
     *
     * @param context reference to the context where the WebView is instantiated.
     * @param delegate reference to the object implementing WebViewWrapperDelegate interface.
     */
    class CustomWebView(context: Context, delegate: WebViewWrapperDelegate) : WebView(context) {
        val viewId: Long = System.currentTimeMillis()
        val webChromeClientWrapper = WebChromeClientWrapper(context, delegate, viewId)

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

    /**
     * Helper method for creating a CustomWebView instance for the host application.
     *
     * @param context reference to the context where the WebView is instantiated.
     * @param delegate reference to the object implementing WebViewWrapperDelegate interface.
     */
    fun createWebView(context: Context, delegate : WebViewWrapperDelegate) : CustomWebView {
        return CustomWebView(context, delegate)
    }

}
