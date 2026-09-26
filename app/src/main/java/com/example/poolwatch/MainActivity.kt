package com.example.poolwatch

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Minimal Wear OS / Oppo Watch 2 entry point.
 * No Compose, no heavy libraries → low RAM & battery friendly.
 */
class MainActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on while playing, but short timeout is fine for watches
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = GameView(this)
        val root = FrameLayout(this)
        root.addView(gameView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    override fun onPause() {
        super.onPause()
        // surfaceDestroyed will stop the thread
    }

    override fun onResume() {
        super.onResume()
    }
}
