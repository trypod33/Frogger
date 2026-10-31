package com.trypod33.frogger

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val v: View = try {
            GameView(this)
        } catch (t: Throwable) {
            val tv = TextView(this)
            tv.text = "STARTUP CRASH\n\n" + t.stackTraceToString()
            tv.setTextColor(Color.WHITE)
            tv.setTextSize(12f)
            tv.setPadding(24, 24, 24, 24)
            tv
        }
        setContentView(v)
    }
}
