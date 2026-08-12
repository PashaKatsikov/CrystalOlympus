package com.crystalolympus.crystalolympusgame

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.crystalolympus.crystalolympusgame.engine.AudioEngine
import com.crystalolympus.crystalolympusgame.engine.GameSound
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButton
import com.crystalolympus.crystalolympusgame.ui.components.OlympusButtonStyle
import com.crystalolympus.crystalolympusgame.ui.components.OlympusIconButton
import com.crystalolympus.crystalolympusgame.ui.theme.CrystalOlympusTheme
import com.crystalolympus.crystalolympusgame.ui.theme.OlympusColors

/** Opens the Privacy Policy and Support pages without leaving the game. */
class WebViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()

        setContent {
            CrystalOlympusTheme {
                WebPageScreen(title = title, url = url, onClose = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_URL = "extra_url"

        const val PRIVACY_POLICY_URL = "https://crystalolympus.com/privacy-policy.html"
        const val SUPPORT_URL = "https://crystalolympus.com/support.html"

        fun open(context: Context, title: String, url: String) {
            context.startActivity(
                Intent(context, WebViewActivity::class.java)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_URL, url),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPageScreen(title: String, url: String, onClose: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    fun goBackOrClose() {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    BackHandler { goBackOrClose() }

    Column(
        Modifier
            .fillMaxSize()
            .background(OlympusColors.Night)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OlympusColors.DeepBlue)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OlympusIconButton(
                onClick = {
                    AudioEngine.play(GameSound.MENU_CLOSE)
                    goBackOrClose()
                },
                size = 36.dp,
            ) {
                Text("<", color = OlympusColors.GoldBright, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                color = OlympusColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                Text("Loading...", color = OlympusColors.TextSecondary, fontSize = 11.sp)
            }
        }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(android.graphics.Color.WHITE)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                                failed = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    loading = false
                                    failed = true
                                }
                            }
                        }
                        webView = this
                        loadUrl(url)
                    }
                },
            )

            if (failed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OlympusColors.Night)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "The page could not be opened",
                        color = OlympusColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Check your connection and try again.",
                        color = OlympusColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row {
                        OlympusButton(text = "RETRY", onClick = { failed = false; webView?.reload() })
                        Spacer(Modifier.width(12.dp))
                        OlympusButton(text = "CLOSE", onClick = onClose, style = OlympusButtonStyle.Blue)
                    }
                }
            }
        }
    }
}
