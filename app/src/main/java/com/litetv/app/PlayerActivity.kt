package com.litetv.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var title: TextView
    private lateinit var message: TextView

    private var index = 0
    private var retries = 0
    private val handler = Handler(Looper.getMainLooper())
    private val hideTitle = Runnable { title.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        view = findViewById(R.id.player)
        title = findViewById(R.id.title)
        message = findViewById(R.id.message)
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        view.controllerShowTimeoutMs = 4000

        if (Store.current.isEmpty()) {
            finish()
            return
        }
        index = Store.index.coerceIn(0, Store.current.size - 1)
    }

    override fun onStart() {
        super.onStart()
        startPlayer()
    }

    override fun onStop() {
        super.onStop()
        stopPlayer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    private fun startPlayer() {
        if (player != null || Store.current.isEmpty()) return

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11; TV) LiteTV/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)

        // Buffers cortos: el canal arranca rápido y cambiar de canal es ágil.
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 1500, 3000)
            .build()

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(load)
            .build()

        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    retries = 0
                    message.visibility = View.GONE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handleError(error)
            }
        })

        player = p
        view.player = p
        play()
    }

    private fun stopPlayer() {
        handler.removeCallbacksAndMessages(null)
        view.player = null
        player?.release()
        player = null
    }

    private fun play() {
        val p = player ?: return
        val ch = Store.current.getOrNull(index) ?: return
        Store.index = index

        handler.removeCallbacksAndMessages(null)
        message.visibility = View.GONE
        p.setMediaItem(MediaItem.fromUri(ch.url))
        p.prepare()
        p.playWhenReady = true

        title.text = "${index + 1}. ${ch.name}"
        title.visibility = View.VISIBLE
        handler.postDelayed(hideTitle, 3500)
    }

    private fun handleError(error: PlaybackException) {
        val p = player ?: return
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            p.seekToDefaultPosition()
            p.prepare()
            return
        }
        if (retries < 3) {
            retries++
            handler.postDelayed({ player?.prepare() }, 1500L * retries)
        } else {
            message.text = "No se pudo reproducir este canal.\nUsa ▲ ▼ para cambiar de canal."
            message.visibility = View.VISIBLE
        }
    }

    private fun zap(delta: Int) {
        val size = Store.current.size
        if (size == 0) return
        index = (index + delta + size) % size
        retries = 0
        play()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val zapDelta = when (e.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> 1
            KeyEvent.KEYCODE_CHANNEL_DOWN -> -1
            KeyEvent.KEYCODE_DPAD_UP -> if (view.isControllerFullyVisible) 0 else -1
            KeyEvent.KEYCODE_DPAD_DOWN -> if (view.isControllerFullyVisible) 0 else 1
            else -> 0
        }
        if (zapDelta != 0) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) zap(zapDelta)
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (view.isControllerFullyVisible) {
            view.hideController()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
