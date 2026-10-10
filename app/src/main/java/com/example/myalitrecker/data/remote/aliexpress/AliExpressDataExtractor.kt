package com.example.myalitrecker.data.remote.aliexpress

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.regex.Pattern

object AliExpressDataExtractor {

    private val ORDER_ID_PATTERN = Pattern.compile("\\b(3\\d{15}|8\\d{15}|5\\d{15})\\b")
    private val TRACKING_PATTERN = Pattern.compile(
        "\\b(AECA[0-9A-Z]{8,22}|AP\\d{12,18}|LP\\d{12,18}|[A-Z]{2}\\d{9}[A-Z]{2}|[A-Z]{2}\\d{12,16})\\b",
        Pattern.CASE_INSENSITIVE
    )

    fun parseFromHtml(html: String): List<AliExpressOrder> {
        val orders = mutableListOf<AliExpressOrder>()

        // 1. Try to find embedded JSON in scripts (runParams, __INITIAL_DATA__, etc.)
        val embeddedJsonOrders = extractEmbeddedJson(html)
        if (embeddedJsonOrders.isNotEmpty()) {
            return embeddedJsonOrders
        }

        // 2. DOM parsing via Jsoup
        try {
            val doc = Jsoup.parse(html)

            // Select possible order containers or cards
            var orderElements = doc.select(".order-item, .order-card, .order-list-item, [data-order-id], [class*='order-item'], [class*='orderItem'], [class*='orderCard']")
            if (orderElements.isEmpty()) {
                // Broader search: any element containing 16-digit order ID and an image
                orderElements = doc.select("div:has(img)")
            }

            for (element in orderElements) {
                val text = element.text()
                val orderIdMatcher = ORDER_ID_PATTERN.matcher(text)
                if (!orderIdMatcher.find()) continue

                val orderId = orderIdMatcher.group(1) ?: continue
                if (orders.any { it.orderId == orderId }) continue

                // Check for tracking number in text or links
                val trackingMatcher = TRACKING_PATTERN.matcher(element.html())
                val trackingNumber = if (trackingMatcher.find()) trackingMatcher.group(1) else null

                // Extract products
                val items = mutableListOf<AliExpressOrderItem>()
                val imgElements = element.select("img")
                for (img in imgElements) {
                    val src = img.attr("abs:src")
                        .ifBlank { img.attr("src") }
                        .ifBlank { img.attr("data-src") }
                    val alt = img.attr("alt")
                        .ifBlank { img.attr("title") }

                    if (src.contains("alicdn.com") && !src.contains("avatar") && !src.contains("icon")) {
                        val fullImg = if (src.startsWith("//")) "https:$src" else src
                        items.add(
                            AliExpressOrderItem(
                                orderId = orderId,
                                title = alt.ifBlank { "Товар из заказа $orderId" },
                                imageUrl = fullImg,
                                quantity = 1
                            )
                        )
                    }
                }

                // If no images found but order ID exists, create placeholder item
                if (items.isEmpty()) {
                    items.add(
                        AliExpressOrderItem(
                            orderId = orderId,
                            title = "Заказ AliExpress $orderId",
                            quantity = 1
                        )
                    )
                }

                orders.add(
                    AliExpressOrder(
                        orderId = orderId,
                        orderStatus = if (trackingNumber != null) "В пути" else "Ожидает отправки",
                        trackingNumber = trackingNumber,
                        items = items
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return orders
    }

    fun parseFromJson(jsonString: String): List<AliExpressOrder> {
        val orders = mutableListOf<AliExpressOrder>()
        try {
            val root = JSONObject(jsonString)
            // Recursively search for any array containing order objects
            val orderArray = findJsonArray(root, listOf("orders", "orderList", "data", "items", "moduleList", "records"))
                ?: return emptyList()

            for (i in 0 until orderArray.length()) {
                val orderObj = orderArray.optJSONObject(i) ?: continue
                val order = parseSingleOrderJson(orderObj)
                if (order != null) {
                    orders.add(order)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return orders
    }

    private fun parseSingleOrderJson(obj: JSONObject): AliExpressOrder? {
        val orderId = obj.optString("orderId").ifBlank {
            obj.optString("id").ifBlank {
                obj.optString("tradeOrderId").ifBlank {
                    // Search in string representation if nested
                    val m = ORDER_ID_PATTERN.matcher(obj.toString())
                    if (m.find()) m.group(1) else ""
                }
            }
        }
        if (orderId.isNullOrBlank()) return null

        val status = obj.optString("orderStatus").ifBlank {
            obj.optString("statusDesc").ifBlank {
                obj.optString("status", "В обработке")
            }
        }

        // Tracking / mailNo
        var trackingNumber: String? = null
        var carrier: String? = null
        var isConsolidated = false

        val logistics = obj.optJSONObject("logistics")
            ?: obj.optJSONObject("sendGoods")
            ?: obj.optJSONObject("packageInfo")

        if (logistics != null) {
            trackingNumber = logistics.optString("mailNo").ifBlank {
                logistics.optString("trackingNo").ifBlank { null }
            }
            carrier = logistics.optString("logisticsServiceName").ifBlank { null }
            isConsolidated = logistics.optBoolean("isConsolidated", false)
        }

        if (trackingNumber.isNullOrBlank()) {
            val rawText = obj.toString()
            val matcher = TRACKING_PATTERN.matcher(rawText)
            if (matcher.find()) {
                trackingNumber = matcher.group(1)
            }
        }

        // Parse items
        val items = mutableListOf<AliExpressOrderItem>()
        val productArray = findJsonArray(obj, listOf("orderLines", "products", "items", "childOrders", "productList"))
        if (productArray != null) {
            for (j in 0 until productArray.length()) {
                val itemObj = productArray.optJSONObject(j) ?: continue
                val title = itemObj.optString("productName").ifBlank {
                    itemObj.optString("title").ifBlank {
                        itemObj.optString("name", "Товар AliExpress")
                    }
                }
                var img = itemObj.optString("productImage").ifBlank {
                    itemObj.optString("imageUrl").ifBlank {
                        itemObj.optString("image", null)
                    }
                }
                if (img != null && img.startsWith("//")) {
                    img = "https:$img"
                }

                val price = itemObj.optString("itemPrice").ifBlank {
                    itemObj.optString("price", null)
                }
                val qty = itemObj.optInt("quantity", 1)

                items.add(
                    AliExpressOrderItem(
                        orderId = orderId,
                        title = title,
                        imageUrl = img,
                        price = price,
                        quantity = qty
                    )
                )
            }
        }

        if (items.isEmpty()) {
            items.add(
                AliExpressOrderItem(
                    orderId = orderId,
                    title = "Заказ AliExpress $orderId",
                    quantity = 1
                )
            )
        }

        return AliExpressOrder(
            orderId = orderId,
            orderStatus = status,
            trackingNumber = trackingNumber,
            carrier = carrier,
            isConsolidated = isConsolidated,
            items = items
        )
    }

    private fun extractEmbeddedJson(html: String): List<AliExpressOrder> {
        val markers = listOf(
            "window.runParams = ",
            "window.__INITIAL_DATA__ = ",
            "runParams = "
        )
        for (marker in markers) {
            val idx = html.indexOf(marker)
            if (idx != -1) {
                try {
                    val start = idx + marker.length
                    var end = html.indexOf(";</script>", start)
                    if (end == -1) end = html.indexOf(";\n", start)
                    if (end != -1 && end > start) {
                        val jsonStr = html.substring(start, end).trim()
                        val parsed = parseFromJson(jsonStr)
                        if (parsed.isNotEmpty()) return parsed
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return emptyList()
    }

    private fun findJsonArray(obj: JSONObject, keys: List<String>): JSONArray? {
        for (key in keys) {
            val arr = obj.optJSONArray(key)
            if (arr != null) return arr
        }
        val iter = obj.keys()
        while (iter.hasNext()) {
            val k = iter.next()
            val child = obj.optJSONObject(k)
            if (child != null) {
                val found = findJsonArray(child, keys)
                if (found != null) return found
            }
        }
        return null
    }
}
