package com.alair.fruitslice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Launch splash: shows "Created By Alair" for a moment, then enters the game.
 *
 * Kid-proof rules apply here too: fullscreen, back key swallowed, and a tap
 * anywhere skips straight to the game (no waiting for impatient fingers).
 */
class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var navigated = false

    private val goNext = Runnable {
        if (navigated || isFinishing) return@Runnable
        navigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_splash)
        window.decorView.setOnSystemUiVisibilityChangeListener { applyImmersive() }
        applyImmersive()
        handler.postDelayed(goNext, SPLASH_MS)
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    // Tap anywhere: skip the splash immediately.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !navigated) {
            handler.removeCallbacks(goNext)
            handler.post(goNext)
        }
        return true
    }

    // Back key swallowed on the first screen too.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally empty.
    }

    override fun onDestroy() {
        handler.removeCallbacks(goNext)
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_MS = 1800L
    }
}
