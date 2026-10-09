package com.example.myalitrecker.data.parser

import com.example.myalitrecker.data.model.ParsedAliExpressEmail
import com.example.myalitrecker.data.model.ParsedItem
import org.jsoup.Jsoup
import java.util.regex.Pattern

object AliExpressEmailParser {

    private val TRACKING_REGEX = Pattern.compile(
        "(?:tracking|номер отслеживания|трек|трек-номер|track(?:ing)?\\s*(?:no|number)?)[:\\s#]*([A-Z0-9]{8,25})",
        Pattern.CASE_INSENSITIVE
    )

    private val ORDER_ID_REGEX = Pattern.compile(
        "(?:order|заказ|order\\s*id|order\\s*no)[:\\s#]*(\\d{10,18})",
        Pattern.CASE_INSENSITIVE
    )

    private val FALLBACK_TRACKING_PATTERN = Pattern.compile(
        "\\b(AECA\\d{9,15}[A-Z0-9]*|LP\\d{12,16}|[A-Z]{2}\\d{9}[A-Z]{2}|NLE[A-Z0-9]{10,15}|SY[A-Z0-9]{8,14}|YT\\d{12,16}|\\d{12,15})\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val CONSOLIDATION_REGEX = Pattern.compile(
        "(?:combined\\s*delivery|consolidat|объединен|сборн|заказы\\s*объединены|upgraded\\s*to\\s*combined)",
        Pattern.CASE_INSENSITIVE
    )

    private val PRICE_REGEX = Pattern.compile(
        "([$€₴₽]\\s*\\d+(?:[.,]\\d{2})?|\\d+(?:[.,]\\d{2})?\\s*[$€₴₽]|\\d+(?:[.,]\\d{2})?\\s*(?:USD|EUR|UAH|RUB|руб))",
        Pattern.CASE_INSENSITIVE
    )

    fun parseEmail(emailId: String, htmlBody: String, date: Long): ParsedAliExpressEmail {
        val doc = Jsoup.parse(htmlBody)
        val textContent = doc.text()

        // 1. Detect if this email is a Consolidation / Combined Delivery notice
        val isConsolidated = CONSOLIDATION_REGEX.matcher(textContent).find()

        // 2. Extract all Order IDs
        val orderIds = extractAllOrderIds(textContent, doc)
        val primaryOrderId = orderIds.firstOrNull()

        // 3. Extract Tracking Number
        var trackingNumber = extractTrackingNumber(textContent)
        if (trackingNumber == null) {
            // Search links for tracking parameters
            for (element in doc.select("a[href]")) {
                val href = element.attr("href")
                val matcher = Pattern.compile("(?:tracking_number|trackingNo|mailNo)=([A-Z0-9]{8,25})", Pattern.CASE_INSENSITIVE).matcher(href)
                if (matcher.find()) {
                    trackingNumber = matcher.group(1)
                    break
                }
            }
        }

        // 4. Extract Items (Title + Image URL + Price)
        val items = mutableListOf<ParsedItem>()
        val defaultOrderId = primaryOrderId ?: "UNKNOWN_$emailId"

        // Search product cards / tables or product images
        val imgElements = doc.select("img[src*=alicdn.com], img[src*=aliexpress]")
        var orderIdIndex = 0

        for (img in imgElements) {
            val src = img.attr("src")
            // Ignore icons, logos, avatars, buttons, tracking pixels
            if (src.contains("logo") || src.contains("icon") || src.contains("avatar") || src.contains("banner") || src.contains("badge")) {
                continue
            }

            var title = img.attr("alt")
            if (title.isBlank()) {
                title = img.attr("title")
            }

            // If alt/title is empty, search adjacent text or parent <a> tag
            if (title.isBlank()) {
                val parentLink = img.closest("a")
                if (parentLink != null) {
                    title = parentLink.text().trim()
                }
            }

            // Look for price near the image
            val parentContainer = img.parents().firstOrNull { it.tagName() == "tr" || it.tagName() == "div" || it.tagName() == "td" }
            val priceText = parentContainer?.let { extractPrice(it.text()) }

            if (title.isNotBlank() && title.length > 3) {
                // Determine which orderId this item belongs to
                val itemOrderId = if (orderIds.isNotEmpty()) {
                    orderIds[orderIdIndex.coerceAtMost(orderIds.size - 1)]
                } else {
                    defaultOrderId
                }
                orderIdIndex++

                val formattedImageUrl = when {
                    src.startsWith("//") -> "https:$src"
                    src.startsWith("http") -> src
                    else -> "https://$src"
                }

                items.add(
                    ParsedItem(
                        orderId = itemOrderId,
                        title = title.take(150),
                        imageUrl = formattedImageUrl,
                        price = priceText
                    )
                )
            }
        }

        // Fallback item if no images found but text exists
        if (items.isEmpty()) {
            val subjectOrSnippet = doc.select("h1, h2, h3, title").text().ifBlank { "Товар с AliExpress" }
            items.add(
                ParsedItem(
                    orderId = defaultOrderId,
                    title = subjectOrSnippet.take(100),
                    imageUrl = null,
                    price = extractPrice(textContent)
                )
            )
        }

        return ParsedAliExpressEmail(
            emailId = emailId,
            orderId = primaryOrderId,
            orderIds = orderIds,
            trackingNumber = trackingNumber,
            items = items,
            date = date,
            isConsolidated = isConsolidated || orderIds.size > 1
        )
    }

    private fun extractAllOrderIds(text: String, doc: org.jsoup.nodes.Document): List<String> {
        val results = mutableSetOf<String>()
        val matcher = ORDER_ID_REGEX.matcher(text)
        while (matcher.find()) {
            matcher.group(1)?.let { results.add(it) }
        }

        // Check links with orderId parameters
        for (element in doc.select("a[href]")) {
            val href = element.attr("href")
            val linkMatcher = Pattern.compile("orderId=(\\d{10,18})", Pattern.CASE_INSENSITIVE).matcher(href)
            while (linkMatcher.find()) {
                linkMatcher.group(1)?.let { results.add(it) }
            }
        }
        return results.toList()
    }

    private fun extractTrackingNumber(text: String): String? {
        val matcher = TRACKING_REGEX.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)
        }
        val fallbackMatcher = FALLBACK_TRACKING_PATTERN.matcher(text)
        if (fallbackMatcher.find()) {
            return fallbackMatcher.group(1)
        }
        return null
    }

    private fun extractPrice(text: String): String? {
        val matcher = PRICE_REGEX.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }
}
