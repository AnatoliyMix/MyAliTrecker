package com.example.myalitrecker.data.remote.aliexpress

import android.content.Context
import com.example.myalitrecker.data.local.AppDatabase
import com.example.myalitrecker.data.local.entity.OrderItemEntity
import com.example.myalitrecker.data.local.entity.ParcelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup

class AliExpressOrderRepository(private val context: Context) {

    private val sessionManager = AliExpressSessionManager(context)
    private val db = AppDatabase.getInstance(context)
    private val parcelDao = db.parcelDao()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
        private const val MOBILE_ORDER_URL = "https://m.aliexpress.com/p/order/index.html"
        private const val DESKTOP_ORDER_URL = "https://trade.aliexpress.com/orderList.htm"
    }

    suspend fun syncOrders(): Result<Int> = withContext(Dispatchers.IO) {
        val cookies = sessionManager.getCookies()
        if (cookies.isBlank()) {
            return@withContext Result.failure(Exception("Сессия AliExpress отсутствует. Пожалуйста, выполните вход."))
        }

        try {
            // Parse cookies string into Map<String, String>
            val cookieMap = mutableMapOf<String, String>()
            for (part in cookies.split(";")) {
                val trimmed = part.trim()
                val eqIdx = trimmed.indexOf("=")
                if (eqIdx != -1) {
                    val k = trimmed.substring(0, eqIdx).trim()
                    val v = trimmed.substring(eqIdx + 1).trim()
                    if (k.isNotEmpty()) {
                        cookieMap[k] = v
                    }
                }
            }

            // 1. Fetch order page with real session cookies
            var orders = fetchOrdersFromUrl(MOBILE_ORDER_URL, cookieMap)
            if (orders.isEmpty()) {
                orders = fetchOrdersFromUrl(DESKTOP_ORDER_URL, cookieMap)
            }

            if (orders.isEmpty()) {
                // If neither returned, might be session expiration
                if (orders.isEmpty() && !sessionManager.isLoggedIn()) {
                    return@withContext Result.failure(Exception("Срок действия сессии AliExpress истёк. Пожалуйста, войдите снова."))
                }
                return@withContext Result.success(0)
            }

            // 2. Persist orders into Room database
            saveOrdersToDatabase(orders)
            sessionManager.updateLastSyncTime()

            Result.success(orders.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun saveOrdersDirectly(orders: List<AliExpressOrder>) = withContext(Dispatchers.IO) {
        saveOrdersToDatabase(orders)
    }

    private fun fetchOrdersFromUrl(url: String, cookieMap: Map<String, String>): List<AliExpressOrder> {
        return try {
            val response: Connection.Response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .cookies(cookieMap)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7,uk;q=0.6")
                .timeout(20000)
                .followRedirects(true)
                .method(Connection.Method.GET)
                .execute()

            val body = response.body()
            AliExpressDataExtractor.parseFromHtml(body)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun saveOrdersToDatabase(orders: List<AliExpressOrder>) {
        for (order in orders) {
            val hasTracking = !order.trackingNumber.isNullOrBlank()
            val trackingNum = if (hasTracking) order.trackingNumber!! else "PENDING_${order.orderId}"

            val carrierName = order.carrier ?: detectCarrier(trackingNum)
            val statusText = if (hasTracking) {
                if (order.isConsolidated) "Объединенная доставка" else "В пути ($carrierName)"
            } else {
                "Ожидает отправки продавцом"
            }

            val parcelEntity = ParcelEntity(
                trackingNumber = trackingNum,
                status = statusText,
                carrier = carrierName,
                lastUpdated = order.orderDate,
                isConsolidated = order.isConsolidated
            )

            val itemEntities = order.items.map { item ->
                OrderItemEntity(
                    orderId = item.orderId,
                    trackingNumber = trackingNum,
                    title = item.title,
                    imageUrl = item.imageUrl,
                    price = item.price,
                    quantity = item.quantity
                )
            }

            parcelDao.insertOrConsolidateParcel(
                parcel = parcelEntity,
                items = itemEntities,
                orderIdsToConsolidate = listOf(order.orderId)
            )
        }
    }

    private fun detectCarrier(trackingNumber: String): String {
        val clean = trackingNumber.trim().uppercase()
        return when {
            clean.startsWith("AECA") -> "Cainiao Consolidated Line"
            clean.startsWith("AP") -> "Cainiao Express (Китай)"
            clean.startsWith("LP") -> "Cainiao Super Economy"
            clean.endsWith("CN") -> "China Post"
            clean.endsWith("NL") -> "PostNL"
            clean.startsWith("PENDING_") -> "AliExpress"
            else -> "AliExpress Standard Shipping"
        }
    }
}
