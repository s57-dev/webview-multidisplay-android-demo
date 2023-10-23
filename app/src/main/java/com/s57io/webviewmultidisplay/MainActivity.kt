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

import android.Manifest
import android.annotation.SuppressLint
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Button
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * `MainActivity` is the hub for managing WebView instances across displays. It utilizes
 * `WebViewWrapper` for WebView creation and management, and implements
 * `WebViewWrapperDelegate` for WebView interaction callbacks.
 *
 * Display numbers are communicated to web applications via `JavascriptBridge`.
 * Target displays are obtained by evaluating `getTargetDisplayIndex` for loaded
 * pages.
 *
 * `WebViewWrapper` is used for WebView creation, while `MainActivity` manages
 * display and user interactions of these WebViews.
 *
 * 1. **Initialization:**
 *    - Sets up main WebView and requests permissions in `onCreate`.
 *
 * 2. **Main WebView Setup:**
 *    - Initializes primary WebView with a URL in `setupMainWebView`.
 *
 * 3. **Presentation Displays Management:**
 *    - Discovers secondary displays and initializes `PresentationDisplay`
 *      instances in `initializePresentationDisplays`.
 *
 * 4. **WebView Management:**
 *    - Creates root WebView in `createMainWebView`.
 *    - Manages window pop-up and full screen requests in `createBrowserWindow`.
 *    - Adds and removes WebViews from layout in `addWebView` and
 *      `removeChildFromParent`.
 *
 * 5. **WebViewWrapperDelegate Implementations:**
 *    - Displays WebView in a new window or fullscreen in `showWindow` and
 *      `showFullscreen`.
 *    - Modifies WebView visibility in `setWebViewVisibility`.
 *    - Handles closing of fullscreen WebView in `onCloseFullscreen`.
 *    - Assigns WebView to correct display in `onPageFinished`.
 *
 * 6. **JavaScript Interface:**
 *    - Adds JavaScript interface in `addJSInterface`.
 *    - `JavascriptBridge` provides JavaScript methods like `getConnectedDisplays`.
 */

class MainActivity : AppCompatActivity(), WebViewWrapper.WebViewWrapperDelegate {

    private val activeViewsMap : HashMap<Long, View> = HashMap()
    private val activeDisplaysMap : HashMap<Long, Int> = HashMap()
    private val webViewWrapper: WebViewWrapper = WebViewWrapper()
    private var presentationDisplays = mutableListOf<PresentationDisplay>()
    private val WEBLAUNCHER_PERMISSIONS = 101
    private val javascriptBridge = JavascriptBridge()
    private val TAG = "WWMDMainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializePresentationDisplays()
        setupMainWebView(Constants.WEB_URL)

        requestPermissions(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ),
            WEBLAUNCHER_PERMISSIONS
        )
    }

    private fun setupMainWebView(url: String) {
        val itemsLayout: RelativeLayout = findViewById(R.id.main_container)
        itemsLayout.addView(createMainWebView(url))
    }

    private fun initializePresentationDisplays() {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val allDisplays = displayManager.displays

        allDisplays.filter { it.displayId != Display.DEFAULT_DISPLAY }.forEach { display ->
            presentationDisplays.add(PresentationDisplay(this, display).apply { show() })
        }
    }

    private fun createMainWebView(url : String) : View {
        val view = layoutInflater.inflate(R.layout.webview_window, null)

        val webView : WebViewWrapper.CustomWebView = webViewWrapper.createWebView(this@MainActivity, this)
        Log.d(Constants.LOG_TAG, "createMainWebView UA: $webView.settings.userAgentString")

        webView.loadUrl(url)

        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val container: RelativeLayout = view.findViewById(R.id.viewContainer)
        val closeButton: Button = view.findViewById(R.id.closeButton)
        closeButton.visibility = View.GONE
        container.addView(webView)
        activeViewsMap[webView.viewId] = webView
        activeDisplaysMap[webView.viewId] = 0
        return view;
    }

    private fun removeChildFromParent(view: View?) {
        if (view != null && view.parent != null) {
            (view.parent as ViewGroup).removeView(view)
        }
    }

    private fun createBrowserWindow(webView : View,
                                    delegate: WebViewWrapper.WebViewWrapperActionDelegate,
                                    viewId : Long,
                                    closeButtonText : String,
                                    onRemove: () -> Unit) : View {

        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val view = layoutInflater.inflate(R.layout.webview_window, null)

        val closeButton: Button = view.findViewById(R.id.closeButton)
        closeButton.text = closeButtonText
        closeButton.setOnClickListener {
            removeChildFromParent(webView)
            removeChildFromParent(view)
            delegate.closeAction()
            if (webView is WebView) {
                Log.d(Constants.LOG_TAG, "Destroying WebView")
                webView.destroy()
                onRemove()
            }
        }

        val container: RelativeLayout = view.findViewById(R.id.viewContainer)
        container.addView(webView)
        return view;
    }

    private fun addWebView(view : View) {
        val itemsLayout: RelativeLayout = findViewById(R.id.main_container)
        itemsLayout.addView(view)
    }

    override fun showWindow(view: WebView, delegate: WebViewWrapper.WebViewWrapperActionDelegate, viewId: Long) {
        Log.d(Constants.LOG_TAG, "showWindow($viewId)")

        val window = createBrowserWindow(view, delegate, viewId, "Close window") {
            activeViewsMap.remove(viewId)
            activeDisplaysMap.remove(viewId)
        }
        activeViewsMap[viewId] = window
    }

    override fun showFullscreen(view: View, delegate: WebViewWrapper.WebViewWrapperActionDelegate, viewId: Long) {
        Log.d(Constants.LOG_TAG, "showFullscreen($viewId)")

        val window = createBrowserWindow(view, delegate, viewId, "Close fullscreen") {
            activeViewsMap.remove(viewId)
            activeDisplaysMap.remove(viewId)
        }
        val displayIndex = activeDisplaysMap[-viewId]
        if (displayIndex != null) {
            if (displayIndex == 0) {
                addWebView(window)
            } else {
                val adjustedIndex = displayIndex - 1
                if (adjustedIndex in presentationDisplays.indices) {
                    presentationDisplays[adjustedIndex].addWebView(window)
                } else {
                    Log.e(Constants.LOG_TAG, "Invalid display index: $adjustedIndex")
                }
            }
            activeViewsMap[viewId] = window
        }
    }

    override fun setWebViewVisibility(viewId: Long, visibility: Int) {
        Log.d(Constants.LOG_TAG, "setWebViewVisibility($viewId): $visibility")
        val webViewWindow = activeViewsMap[viewId]
        webViewWindow!!.visibility = visibility
    }

    override fun onCloseFullscreen(viewId: Long) {
        if (activeViewsMap.contains(viewId)) {
            Log.d(Constants.LOG_TAG, "onCloseFullscreen($viewId)")
            val webViewWindow = activeViewsMap[viewId]
            webViewWindow!!.visibility = View.GONE
            removeChildFromParent(webViewWindow)
            activeViewsMap.remove(viewId)
            activeDisplaysMap.remove(viewId)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?, viewId: Long) {
        Log.d(Constants.LOG_TAG, "onPageFinished($viewId)")

        fun addWebViewToDisplay(displayIndex: Int) {
            Log.d(TAG, "add to display: $displayIndex")
            activeViewsMap[viewId]?.let { webView ->
                if (displayIndex == 0) {
                    addWebView(webView)
                } else {
                    val adjustedIndex = displayIndex - 1
                    if (adjustedIndex in presentationDisplays.indices) {
                        presentationDisplays[adjustedIndex].addWebView(webView)
                    } else {
                        Log.e(Constants.LOG_TAG, "Invalid display index: $adjustedIndex")
                    }
                }
            }
        }

        fun handleDisplayIndexResult(result: String) {
            val displayIndex = result.replace("\"", "").toInt()
            Log.d(Constants.LOG_TAG, "onPageFinished($viewId): got displayIndex = $displayIndex")

            if (activeDisplaysMap[viewId] == null) {
                addWebViewToDisplay(displayIndex)
                activeDisplaysMap[viewId] = displayIndex
            }
        }

        view?.evaluateJavascript("getTargetDisplayIndex();") { result ->
            result?.let {
                if (it != "null") {
                    handleDisplayIndexResult(it)
                } else {
                    handleDisplayIndexResult("0")
                }
            }
        }
    }


    @SuppressLint("JavascriptInterface")
    override fun addJSInterface(webView: WebView) {
        Log.d(Constants.LOG_TAG, "addJSInterface")
        webView.addJavascriptInterface(javascriptBridge, "AndroidBridge")
    }

    private inner class JavascriptBridge {

        @JavascriptInterface
        fun getConnectedDisplays(): Int {
            val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
            val displayNum = displayManager.displays.size
            Log.i(TAG, "Found $displayNum displays connected.")
            return displayNum
        }

    }
}
