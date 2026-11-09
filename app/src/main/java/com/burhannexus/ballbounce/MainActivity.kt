package com.burhannexus.ballbounce

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var gameView: GameView
    private lateinit var sounds: SoundManager
    private lateinit var prefs: PrefsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsStore(this)
        sounds = SoundManager(this, prefs.soundOn)
        gameView = GameView(this, prefs, sounds)
        setContentView(gameView)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() else gameView.onFocusLost()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        sounds.resumeAll()
        gameView.onHostResume()
    }

    override fun onPause() {
        gameView.onHostPause()
        sounds.stopAll()
        super.onPause()
    }

    override fun onDestroy() {
        sounds.release()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!gameView.handleBack()) {
            exitGame()
        }
    }

    /** EXIT GAME: closes the game and returns to the Android home screen. */
    fun exitGame() {
        gameView.onHostPause()
        finishAndRemoveTask()
    }

    fun openEmail() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Brand.EMAIL)).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Ball Bounce")
        }
        launch(intent, "No email app found")
    }

    /** Opens the phone dialer with the number filled in. The user must press Call to confirm. */
    fun openDialer() {
        launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Brand.PHONE)), "No phone app found")
    }

    private fun launch(intent: Intent, failMessage: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, failMessage, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, failMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }
}
