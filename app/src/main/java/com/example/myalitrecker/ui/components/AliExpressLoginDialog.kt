package com.example.myalitrecker.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myalitrecker.data.remote.aliexpress.AliExpressDataExtractor
import com.example.myalitrecker.data.remote.aliexpress.AliExpressOrder
import org.json.JSONTokener

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliExpressLoginDialog(
    initialIsLoggedIn: Boolean = false,
    onDismissRequest: () -> Unit,
    onOrdersExtracted: (orders: List<AliExpressOrder>, cookieString: String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentTitle by remember { mutableStateOf(if (initialIsLoggedIn) "Синхронизация заказов AliExpress" else "Вход в AliExpress") }
    var statusText by remember { mutableStateOf(if (initialIsLoggedIn) "Загружаем страницу ваших заказов..." else "Войдите в аккаунт на официальном сайте") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var hasExtractedOrders by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val orderUrl = "https://www.aliexpress.com/p/order/index.html"
    val loginUrl = "https://m.aliexpress.com/login.html"

    fun extractOrdersFromWebView(wv: WebView, cookies: String) {
        val js = """
            (function() {
                try {
                    if (window.runParams && window.runParams.data) {
                        return JSON.stringify(window.runParams.data);
                    }
                    if (window.runParams && window.runParams.orders) {
                        return JSON.stringify(window.runParams.orders);
                    }
                    if (window.__INITIAL_DATA__) {
                        return JSON.stringify(window.__INITIAL_DATA__);
                    }
                    var scripts = document.querySelectorAll('script');
                    for (var i = 0; i < scripts.length; i++) {
                        var s = scripts[i].innerText || "";
                        if (s.indexOf('orderId') !== -1 || s.indexOf('orderList') !== -1) {
                            var match = s.match(/(\{.*"orderId".*\})/);
                            if (match) return match[1];
                        }
                    }
                    return document.documentElement.outerHTML;
                } catch(e) {
                    return document.documentElement.outerHTML;
                }
            })();
        """.trimIndent()

        wv.evaluateJavascript(js) { result ->
            if (result != null && result != "null" && result != "\"\"") {
                val raw = try {
                    JSONTokener(result).nextValue().toString()
                } catch (e: Exception) {
                    result.removeSurrounding("\"").replace("\\\"", "\"").replace("\\n", "\n")
                }

                val orders = if (raw.trim().startsWith("{")) {
                    AliExpressDataExtractor.parseFromJson(raw)
                } else {
                    AliExpressDataExtractor.parseFromHtml(raw)
                }

                if (orders.isNotEmpty() && !hasExtractedOrders) {
                    hasExtractedOrders = true
                    Toast.makeText(context, "Успешно найдено заказов: ${orders.size}!", Toast.LENGTH_SHORT).show()
                    onOrdersExtracted(orders, cookies)
                } else if (orders.isEmpty()) {
                    Toast.makeText(context, "Идет загрузка страницы... Подождите пару секунд", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                        IconButton(onClick = onDismissRequest) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    }
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // WebView Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                webViewInstance = this

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    // Use modern Chrome desktop/tablet user agent to stop AliExpress from redirecting to native app via aliexpress://
                                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress / 100f
                                        isLoading = newProgress < 100
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val u = request?.url?.toString() ?: return false
                                        return handleDeepLinks(view, u)
                                    }

                                    @Suppress("DEPRECATION")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        val u = url ?: return false
                                        return handleDeepLinks(view, u)
                                    }

                                    private fun handleDeepLinks(view: WebView?, url: String): Boolean {
                                        if (url.startsWith("aliexpress://") || url.startsWith("intent://") || url.startsWith("market://")) {
                                            // Intercept AliExpress app redirect to prevent ERR_UNKNOWN_URL_SCHEME!
                                            val uri = Uri.parse(url)
                                            val nested = uri.getQueryParameter("url")
                                            if (!nested.isNullOrBlank()) {
                                                val decoded = Uri.decode(nested)
                                                view?.loadUrl(decoded)
                                            }
                                            return true // Handled! Do not pass to WebView
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        val currentUrl = url ?: ""
                                        val cookies = cookieManager.getCookie(currentUrl) ?: ""

                                        val isAuth = cookies.contains("xman_us_t") ||
                                                cookies.contains("login_aliyunid_ticket") ||
                                                cookies.contains("intl_common_token")

                                        if (isAuth) {
                                            if (currentUrl.contains("/order") || currentUrl.contains("orderList")) {
                                                currentTitle = "Синхронизация заказов"
                                                statusText = "Страница открыта, считываем заказы..."
                                                // Give 2 seconds for React to finish rendering DOM cards
                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    extractOrdersFromWebView(view ?: this@apply, cookies)
                                                }, 2000)
                                            } else if (!currentUrl.contains("/order") && !currentUrl.contains("login")) {
                                                currentTitle = "Переходим к заказам"
                                                statusText = "Авторизация подтверждена, открываем заказы..."
                                                view?.loadUrl(orderUrl)
                                            }
                                        }
                                    }
                                }

                                val targetUrl = if (initialIsLoggedIn) orderUrl else loginUrl
                                loadUrl(targetUrl)
                            }
                        }
                    )
                }

                // Bottom Action bar to manually capture visible orders
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Видите ваши заказы на экране?",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Нажмите кнопку справа, чтобы считать их в приложение",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                webViewInstance?.let { wv ->
                                    val cookies = CookieManager.getInstance().getCookie(wv.url) ?: ""
                                    extractOrdersFromWebView(wv, cookies)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Считать заказы", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
