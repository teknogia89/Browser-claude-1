package com.example.bebasbrowser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var dlButton: Button
    private var videoUrl: String? = null

    // Daftar host iklan/pop-up. Tambahkan sendiri sesuai kebutuhan.
    private val adHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "adservice.google.com",
        "popads.net", "propellerads.com", "exoclick.com", "adsterra.com",
        "juicyads.com", "taboola.com", "outbrain.com", "popcash.net"
    )

    private fun isAd(host: String) = adHosts.any { host == it || host.endsWith(".$it") }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        urlBar = EditText(this).apply {
            hint = "Cari atau ketik alamat"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        web = WebView(this)
        dlButton = Button(this).apply {
            text = "⬇ Download video"
            visibility = View.GONE
            setOnClickListener { videoUrl?.let { downloadVideo(it) } }
        }

        val root = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(urlBar, LinearLayout.LayoutParams(-1, -2))
        col.addView(web, LinearLayout.LayoutParams(-1, -1))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        root.addView(dlButton, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, 32, 32)
        })
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Blokir pop-up
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }

        web.webChromeClient = object : WebChromeClient() {
            // Tolak semua window.open / pop-up
            override fun onCreateWindow(v: WebView?, d: Boolean, g: Boolean, m: android.os.Message?) = false
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val scheme = r.url.scheme ?: return true
                // Blokir intent:// dan skema aneh yang sering dipakai redirect iklan
                return scheme != "http" && scheme != "https"
            }

            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val url = r.url.toString()
                if (isAd(r.url.host ?: "")) {
                    return WebResourceResponse("text/plain", "utf-8", null)
                }
                // Deteksi video yang sedang di-stream
                val path = r.url.path?.lowercase() ?: ""
                if (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8")) {
                    videoUrl = url
                    runOnUiThread { dlButton.visibility = View.VISIBLE }
                }
                return null
            }

            override fun onPageStarted(v: WebView, url: String, f: android.graphics.Bitmap?) {
                videoUrl = null
                dlButton.visibility = View.GONE
                urlBar.setText(url)
            }
        }

        urlBar.setOnEditorActionListener { _, id, e ->
            if (id == EditorInfo.IME_ACTION_GO || e?.keyCode == KeyEvent.KEYCODE_ENTER) {
                load(urlBar.text.toString().trim()); true
            } else false
        }

        load("https://duckduckgo.com")
    }

    private fun load(input: String) {
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(input)
        }
        web.loadUrl(url)
    }

    private fun downloadVideo(url: String) {
        if (url.lowercase().contains(".m3u8")) {
            Toast.makeText(this, "Stream HLS (.m3u8) perlu digabung dengan FFmpeg, belum didukung di versi ini", Toast.LENGTH_LONG).show()
            return
        }
        val name = Uri.parse(url).lastPathSegment ?: "video.mp4"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            .addRequestHeader("User-Agent", web.settings.userAgentString)
            .addRequestHeader("Referer", web.url ?: "")
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
        (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(this, "Mengunduh $name", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
