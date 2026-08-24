package com.crystalolympus.crystalolympusgame.terrain

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.crystalolympus.crystalolympusgame.R
import com.crystalolympus.crystalolympusgame.almanac.Cartouche

/**
 * The screen that asks for notification permission, ahead of the WebView.
 *
 * Skipping puts it off for three days. Accepting opens the system dialog and
 * takes this screen out of circulation permanently, no matter what the user
 * then tells the dialog — [Cartouche.notifPromoClosed] and pitfalls #36 explain
 * why that is the only pair of states worth tracking.
 *
 * Two buttons, ACCEPT and SKIP, over a branded background picked for the
 * current orientation.
 */
class ConsentCard : AppCompatActivity() {

    private lateinit var vault: Cartouche
    private var pendingUrl: String? = null

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { proceed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = Cartouche(applicationContext)
        pendingUrl = intent.getStringExtra(EXTRA_TARGET_URL)

        val isLandscape = resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val bgRes = if (isLandscape) R.drawable.atl_notif_landscape
                    else R.drawable.atl_notif_portrait

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        val bg = ImageView(this).apply {
            setImageResource(bgRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(bg)

        // Buttons ride along the bottom edge.
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
            lp.bottomMargin = dpToPx(40)
            layoutParams = lp
        }

        val acceptBtn = buildButton("ACCEPT", accent = true)
        val skipBtn   = buildButton("SKIP",   accent = false)

        acceptBtn.setOnClickListener { onAccept() }
        skipBtn.setOnClickListener   { onSkip()   }

        btnRow.addView(acceptBtn)
        btnRow.addView(space(16))
        btnRow.addView(skipBtn)
        root.addView(btnRow)

        setContentView(root)
        com.crystalolympus.crystalolympusgame.ScreenFit.apply(this)
    }

    private fun onAccept() {
        // Set before the dialog goes up, not after it comes back. The answer has
        // no bearing on this screen's fate, and the app can be swiped away while
        // the dialog is still showing — wait for a result and that swipe leaves
        // the promo queued up again over a permission already asked for.
        vault.notifPromoClosed = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            proceed()
            return
        }
        val already = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (already) proceed() else permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun onSkip() {
        vault.snoozeNotifPrompt()
        proceed()
    }

    /**
     * Onward to the shell directly. Routing back via the launcher is avoided on
     * purpose: the splash has already had its turn this launch, and a second
     * showing right after a permission dialog looks to the user like a crash
     * and relaunch.
     */
    private fun proceed() {
        val next = Intent(this, MeridianView::class.java).apply {
            pendingUrl?.let { putExtra(MeridianView.EXTRA_STREAM_URL, it) }
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(next)
        finish()
    }

    private fun buildButton(label: String, accent: Boolean): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = if (accent) 17f else 16f
        tv.gravity = Gravity.CENTER
        tv.setPadding(dpToPx(28), dpToPx(14), dpToPx(28), dpToPx(14))
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.letterSpacing = 0.06f
        if (accent) {
            tv.setBackgroundResource(R.drawable.atl_btn_accept)
            tv.setTextColor(Color.parseColor("#1A0A00"))
            tv.setShadowLayer(2f, 0f, 1f, Color.parseColor("#55FFFFFF"))
        } else {
            tv.setBackgroundResource(R.drawable.atl_btn_skip)
            tv.setTextColor(Color.parseColor("#FFD24A"))
            tv.setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        val lp = LinearLayout.LayoutParams(dpToPx(150), dpToPx(54))
        tv.layoutParams = lp
        return tv
    }

    private fun space(dp: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dpToPx(dp), 1)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    companion object {
        const val EXTRA_TARGET_URL = "alert_target_url"
    }
}
