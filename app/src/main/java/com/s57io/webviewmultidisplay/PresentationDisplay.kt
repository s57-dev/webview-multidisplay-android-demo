package com.s57io.webviewmultidisplay

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.View
import android.widget.RelativeLayout

public class PresentationDisplay(outerContext: Context?, display: Display?) : Presentation(outerContext, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.presentation_display)
    }

    fun addWebView(view : View) {
        var itemsLayout: RelativeLayout = findViewById(R.id.presentation_container)
        itemsLayout.addView(view)
    }

}
