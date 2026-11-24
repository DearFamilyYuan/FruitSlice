package com.alair.fruitslice

import android.app.Activity
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import java.util.Locale

/**
 * Full-screen, kid-proof host for the slicing game.
 *
 * - Immersive sticky fullscreen, re-applied on every focus / system-UI change.
 * - Back key is swallowed: no way to leave the game by mis-tapping.
 * - Keeps the screen on, locks to portrait, no dialogs, no exit button.
 */
class MainActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent screen from sleeping while kids play.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Rotation / dark mode / resize must not recreate the Activity (would reset the game).
        gameView = GameView(this)
        // 10 minutes are up -> freeze on "Time's Up!" and keep reading the
        // reminder aloud on a loop (10 s pause between plays); never exit.
        gameView.onTimeUp = { startNoticeLoop() }
        setContentView(gameView)
        window.decorView.setOnSystemUiVisibilityChangeListener {
            // Nav bar swiped in by accident -> hide it again right away.
            applyImmersive()
        }
        applyImmersive()
    }

    @Suppress("DEPRECATION")
    private val immersiveFlags: Int
        get() = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

    @Suppress("DEPRECATION")
    private fun applyImmersive() {
        window.decorView.systemUiVisibility = immersiveFlags
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersive()
            gameView.onResumeGame()
        } else {
            gameView.onPauseGame()
        }
    }

    /**
     * Swallow the back key entirely: a mis-tap must never leave the game.
     * (Kids can still press HOME; a regular non-system app cannot block that.)
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally empty - never finish() on back.
    }

    /**
     * Time-up loop: the screen stays frozen on "Time's Up!" while the little-girl
     * reminder plays again every NOTICE_INTERVAL_MS. Never exits on its own.
     */
    /**
     * Pick the girl-voice reminder matching the current system language:
     * English systems get the English recording, everything else Chinese.
     */
    private fun noticeResId(): Int =
        if (Locale.getDefault().language.equals("en", ignoreCase = true))
            R.raw.timer_notice_en
        else
            R.raw.timer_notice

    private fun startNoticeLoop() {
        playNotice()
    }

    private fun playNotice() {
        // (Re)pick the voice whenever it actually plays: honors the system
        // language even if it changed while the loop was already running.
        val resId = noticeResId()
        if (noticePlayer == null || noticeRes != resId) {
            noticePlayer?.release()
            noticeRes = resId
            Log.i(TAG, "notice voice: " + if (resId == R.raw.timer_notice_en) "en" else "zh")
            noticePlayer = try {
                MediaPlayer.create(this, resId)
            } catch (e: Exception) {
                null
            }
        }
        val player = noticePlayer ?: return
        if (isFinishing || isDestroyed) return
        try {
            player.seekTo(0)
            player.setOnCompletionListener { scheduleNextNotice() }
            player.start()
        } catch (e: Exception) {
            scheduleNextNotice()
        }
    }

    private fun scheduleNextNotice() {
        val view = gameView
        view.removeCallbacks(noticeRunnable)
        view.postDelayed(noticeRunnable, NOTICE_INTERVAL_MS)
    }

    private val noticeRunnable = Runnable { playNotice() }

    override fun onResume() {
        super.onResume()
        applyImmersive()
        gameView.onResumeGame()
        // back from HOME while the time-up screen is showing: keep the loop going
        if (gameView.isTimeUp && noticePlayer?.isPlaying != true) {
            playNotice()
        }
    }

    override fun onPause() {
        gameView.onPauseGame()
        // silence in the background, resume later
        gameView.removeCallbacks(noticeRunnable)
        noticePlayer?.let {
            if (it.isPlaying) it.pause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        gameView.removeCallbacks(noticeRunnable)
        noticePlayer?.release()
        noticePlayer = null
        super.onDestroy()
    }

    private var noticePlayer: MediaPlayer? = null
    /** Resource currently loaded into noticePlayer (0 = none yet). */
    private var noticeRes: Int = 0

    private companion object {
        private const val TAG = "FruitSlice"
        /** Pause between two plays of the time-up reminder. */
        const val NOTICE_INTERVAL_MS = 10_000L
    }
}
