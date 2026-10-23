package com.tvxbrowser

import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import java.io.ByteArrayInputStream

class MainActivity : Activity() {
    private data class Tab(val web: WebView, var title: String = "New tab")
    private val tabs = mutableListOf<Tab>()
    private var active = 0
    private lateinit var root: LinearLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var address: EditText
    private lateinit var webHost: FrameLayout
    private lateinit var mouseBadge: TextView
    private var mouseMode = false
    private var mouseX = 0.5
    private var mouseY = 0.5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)
        buildUi()
        addTab("https://www.google.com")
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(18, 20, 27))
            setPadding(10, 8, 10, 6)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun button(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setOnClickListener { action() }
        }
        toolbar.addView(button("＋ Tab") { addTab("https://www.google.com") })
        toolbar.addView(button("←") { current()?.web?.goBack() })
        toolbar.addView(button("↻") { current()?.web?.reload() })
        address = EditText(this).apply {
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            hint = "اكتب عنوان الموقع أو ابحث"
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(40, 44, 54))
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(12, 2, 12, 2)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    navigate(text.toString()); true
                } else false
            }
        }
        toolbar.addView(address, LinearLayout.LayoutParams(0, 48, 1f))
        toolbar.addView(button("Mouse") { toggleMouse() })
        toolbar.addView(button("Downloads") { openDownloads() })
        root.addView(toolbar)
        tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabBar)
        webHost = FrameLayout(this)
        root.addView(webHost, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        mouseBadge = TextView(this).apply {
            text = "●"
            textSize = 26f
            setTextColor(Color.YELLOW)
            visibility = View.GONE
            elevation = 30f
        }
        webHost.addView(mouseBadge, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)
    }

    private fun current(): Tab? = tabs.getOrNull(active)

    private fun addTab(url: String) {
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setSupportMultipleWindows(false)
        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                tabs.find { it.web === view }?.let { it.title = title?.take(24) ?: "Tab" }
                refreshTabs()
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val requested = request?.url?.toString() ?: return null
                if (AdBlocker.isBlocked(requested)) {
                    return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(),
                        ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (view === current()?.web && url != null) address.setText(url)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (view === current()?.web && url != null) address.setText(url)
            }
        }
        web.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(contentDisposition ?: Uri.parse(url).lastPathSegment ?: "Download")
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,
                        Uri.parse(url).lastPathSegment ?: "download")
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) request.addRequestHeader("Cookie", cookies)
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this@MainActivity, "بدأ التنزيل", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "تعذر بدء التنزيل", Toast.LENGTH_SHORT).show()
            }
        })
        tabs.add(Tab(web))
        active = tabs.lastIndex
        webHost.addView(web, 0, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        refreshTabs()
        web.loadUrl(url)
    }

    private fun refreshTabs() {
        tabBar.removeAllViews()
        tabs.forEachIndexed { index, tab ->
            val b = Button(this).apply {
                text = (if (index == active) "● " else "") + tab.title
                textSize = 11f
                isAllCaps = false
                setOnClickListener { selectTab(index) }
                setOnLongClickListener { closeTab(index); true }
            }
            tabBar.addView(b, LinearLayout.LayoutParams(0, 42, 1f))
        }
    }

    private fun selectTab(index: Int) {
        if (index !in tabs.indices) return
        active = index
        tabs.forEachIndexed { i, tab -> tab.web.visibility = if (i == active) View.VISIBLE else View.GONE }
        address.setText(current()?.web?.url ?: "")
        refreshTabs()
    }

    private fun closeTab(index: Int) {
        if (tabs.size <= 1) {
            current()?.web?.loadUrl("about:blank")
            return
        }
        val removed = tabs.removeAt(index)
        webHost.removeView(removed.web)
        removed.web.destroy()
        active = active.coerceAtMost(tabs.lastIndex)
        selectTab(active)
    }

    private fun navigate(input: String) {
        val raw = input.trim()
        if (raw.isEmpty()) return
        val url = when {
            raw.startsWith("https://") || raw.startsWith("http://") -> raw
            raw.contains(".") && !raw.contains(" ") -> "https://$raw"
            else -> "https://www.google.com/search?q=" + Uri.encode(raw)
        }
        current()?.web?.loadUrl(url)
        address.clearFocus()
    }

    private fun toggleMouse() {
        mouseMode = !mouseMode
        mouseBadge.visibility = if (mouseMode) View.VISIBLE else View.GONE
        Toast.makeText(this, if (mouseMode) "وضع الماوس: الأسهم للتحريك و OK للنقر" else "وضع التنقل العادي",
            Toast.LENGTH_SHORT).show()
        updateMousePosition()
    }

    private fun updateMousePosition() {
        val w = webHost.width.coerceAtLeast(1)
        val h = webHost.height.coerceAtLeast(1)
        mouseBadge.x = (mouseX * w).toFloat().coerceIn(0f, (w - 30).toFloat())
        mouseBadge.y = (mouseY * h).toFloat().coerceIn(0f, (h - 30).toFloat())
    }

    private fun clickAtMouse() {
        val x = (mouseX * 100).toInt()
        val y = (mouseY * 100).toInt()
        current()?.web?.evaluateJavascript(
            "(function(){var e=document.elementFromPoint(window.innerWidth*$x/100,window.innerHeight*$y/100);if(e)e.click();})()", null
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mouseMode && event.action == KeyEvent.ACTION_DOWN) {
            val step = if (event.isLongPress) 0.06 else 0.025
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { mouseX = (mouseX - step).coerceAtLeast(0.0); updateMousePosition(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { mouseX = (mouseX + step).coerceAtMost(1.0); updateMousePosition(); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { mouseY = (mouseY - step).coerceAtLeast(0.0); updateMousePosition(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { mouseY = (mouseY + step).coerceAtMost(1.0); updateMousePosition(); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { clickAtMouse(); return true }
                KeyEvent.KEYCODE_BACK -> { toggleMouse(); return true }
            }
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
            toggleMouse(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openDownloads() {
        try {
            startActivity(android.content.Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (_: Exception) {
            Toast.makeText(this, "افتح تطبيق الملفات لعرض التنزيلات", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        val tab = current()
        if (tab?.web?.canGoBack() == true) tab.web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        tabs.forEach { it.web.destroy() }
        tabs.clear()
        super.onDestroy()
    }
}
