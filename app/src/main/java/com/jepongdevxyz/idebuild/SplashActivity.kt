package com.jepongdevxyz.idebuild

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.jepongdevxyz.idebuild.databinding.ActivitySplashBinding

/**
 * Pixel-perfect splash per the DevxyzIDE reference poster:
 * logo, wordmark, taglines, progress bar, init caption and
 * "Powered by Jepong Devxyz" footer over a blue wave.
 * Auto-navigates to MainActivity after ~1.8s with real progress.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var b: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private var progress = 0

    private val ticker = object : Runnable {
        override fun run() {
            progress += 4
            if (progress >= 100) {
                b.splashProgress.progress = 100
                goNext()
            } else {
                b.splashProgress.progress = progress
                handler.postDelayed(this, 72)
            }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)
        handler.postDelayed(ticker, 120)
    }

    private fun goNext() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
