package com.example.myalitrecker.data.parser

import com.example.myalitrecker.data.model.ParsedAliExpressEmail
import com.example.myalitrecker.data.model.ParsedItem
import org.jsoup.Jsoup
import java.util.regex.Pattern

object AliExpressEmailParser {

    // Regex patterns for tracking numbers and order IDs
    private val TRACKING_REGEX = Pattern.compile(
        "(?:tracking|номер отслеживания|трек|трек-номер|track(?:ing)?\\s*(?:no|number)?)[:\\s#]*([A-Z0-9]{8,25})",
        Pattern.CASE_INSENSITIVE
    )

    private val ORDER_ID_REGEX = Pattern.compile(
        "(?:order|заказ|order\\s*id|order\\s*no)[:\\s#]*(\\d{10,18})",
        Pattern.CASE_INSENSITIVE
    )

    // Standard AliExpress tracking format fallback regex (e.g. AECA..., LP..., RA...RU, 123456789012)
    private val FALLBACK_TRACKING_PATTERN = Pattern.compile(
        "\\b(AECA\\d{9,15}[A-Z0-9]*|LP\\d{12,16}|[A-Z]{2}\\d{9}[A-Z]{2}|NLE[A-Z0-9]{10,15}|\\d{12,15})\\b",
        Pattern.CASE_INSENSITIVE
    )

    fun parseEmail(emailId: String, htmlBody: String, date: Long): ParsedAliExpressEmail {
        val doc = Jsoup.parse(htmlBody)
        val textContent = doc.text()

        // 1. Extract Order ID
        var orderId = extractOrderId(textContent)
        if (orderId == null) {
            // Check links with orderId parameters
            for (element in doc.select("a[href]")) {
                val href = element.attr("href")
                val matcher = Pattern.compile("orderId=(\\d{10,18})", Pattern.CASE_INSENSITIVE).matcher(href)
                if (matcher.find()) {
                    orderId = matcher.group(1)
                    break
                }
            }
        }

        // 2. Extract Tracking Number
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

        // 3. Extract Items (Title + Image URL)
        val items = mutableListOf<ParsedItem>()
        val defaultOrderId = orderId ?: "UNKNOWN_$emailId"

        // Find product images (usually hosted on alicdn.com)
        val imgElements = doc.select("img[src*=alicdn.com], img[src*=aliexpress]")
        for (img in imgElements) {
            val src = img.attr("src")
            // Ignore icons, logos or tiny tracking pixels
            if (src.contains("logo") || src.contains("icon") || src.contains("avatar") || src.contains("banner")) {
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

            if (title.isNotBlank() && title.length > 3) {
                // Format image url to https
                val formattedImageUrl = if (src.startsWith("//")) "https:$src" else src
                items.add(
                    ParsedItem(
                        orderId = defaultOrderId,
                        title = title,
                        imageUrl = formattedImageUrl
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
                    imageUrl = null
                )
            )
        }

        return ParsedAliExpressEmail(
            emailId = emailId,
            orderId = orderId,
            trackingNumber = trackingNumber,
            items = items,
            date = date
        )
    }

    private fun extractOrderId(text: String): String? {
        val matcher = ORDER_ID_REGEX.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
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
}
