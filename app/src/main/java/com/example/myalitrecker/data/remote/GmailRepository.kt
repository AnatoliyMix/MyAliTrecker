package com.example.myalitrecker.data.remote

import android.content.Context
import android.util.Base64
import com.example.myalitrecker.data.local.AppDatabase
import com.example.myalitrecker.data.local.entity.OrderItemEntity
import com.example.myalitrecker.data.local.entity.ParcelEntity
import com.example.myalitrecker.data.parser.AliExpressEmailParser
import com.example.myalitrecker.util.Constants
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.MessagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

class GmailRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val parcelDao = db.parcelDao()

    suspend fun syncAliExpressEmails(account: GoogleSignInAccount): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                Collections.singleton(Constants.GMAIL_SCOPE)
            )
            credential.selectedAccount = account.account

            val gmailService = Gmail.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("MyAliTrecker").build()

            // 1. Search messages matching AliExpress query
            val listResponse = gmailService.users().messages()
                .list("me")
                .setQ(Constants.GMAIL_ALIEXPRESS_QUERY)
                .setMaxResults(50L)
                .execute()

            val messages = listResponse.messages ?: emptyList()
            var processedCount = 0

            // Fetch and parse all emails
            val parsedEmails = mutableListOf<com.example.myalitrecker.data.model.ParsedAliExpressEmail>()

            for (msgSummary in messages) {
                val fullMsg = gmailService.users().messages()
                    .get("me", msgSummary.id)
                    .setFormat("full")
                    .execute()

                val htmlBody = extractHtmlBody(fullMsg.payload)
                if (htmlBody.isNotBlank()) {
                    val date = fullMsg.internalDate ?: System.currentTimeMillis()
                    val parsed = AliExpressEmailParser.parseEmail(
                        emailId = fullMsg.id,
                        htmlBody = htmlBody,
                        date = date
                    )
                    parsedEmails.add(parsed)
                }
            }

            // Sort chronological (oldest to newest) to process order creation before consolidation updates
            parsedEmails.sortBy { it.date }

            for (parsed in parsedEmails) {
                if (!parsed.trackingNumber.isNullOrBlank() || parsed.items.isNotEmpty()) {
                    val trackingNum = parsed.trackingNumber ?: "PENDING_${parsed.orderId ?: parsed.emailId}"
                    val carrierName = detectCarrier(trackingNum)
                    val statusText = if (parsed.isConsolidated) {
                        "Объединенная доставка (Консолидация)"
                    } else if (trackingNum.startsWith("PENDING_")) {
                        "Ожидает отправки продавцом"
                    } else {
                        "В пути ($carrierName)"
                    }

                    val parcelEntity = ParcelEntity(
                        trackingNumber = trackingNum,
                        status = statusText,
                        carrier = carrierName,
                        lastUpdated = parsed.date,
                        isConsolidated = parsed.isConsolidated
                    )

                    val itemEntities = parsed.items.map { item ->
                        OrderItemEntity(
                            orderId = item.orderId,
                            trackingNumber = trackingNum,
                            title = item.title,
                            imageUrl = item.imageUrl,
                            price = item.price
                        )
                    }

                    // Insert or consolidate: moves previous items from orderIds into this unified parcel
                    parcelDao.insertOrConsolidateParcel(
                        parcel = parcelEntity,
                        items = itemEntities,
                        orderIdsToConsolidate = parsed.orderIds
                    )
                    processedCount++
                }
            }

            Result.success(processedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun detectCarrier(trackingNumber: String): String {
        val clean = trackingNumber.trim().uppercase()
        return when {
            clean.startsWith("AECA") -> "Cainiao Consolidated Line"
            clean.startsWith("LP") -> "Cainiao Super Economy"
            clean.endsWith("CN") -> "China Post"
            clean.endsWith("NL") -> "PostNL"
            clean.startsWith("SY") -> "SunYou"
            clean.startsWith("YT") -> "YunExpress"
            clean.startsWith("PENDING_") -> "AliExpress"
            else -> "AliExpress Standard Shipping"
        }
    }

    private fun extractHtmlBody(part: MessagePart?): String {
        if (part == null) return ""
        if (part.mimeType == "text/html" && part.body?.data != null) {
            return decodeBase64(part.body.data)
        }
        if (part.parts != null) {
            for (subPart in part.parts) {
                val html = extractHtmlBody(subPart)
                if (html.isNotBlank()) return html
            }
        }
        if (part.mimeType == "text/plain" && part.body?.data != null) {
            return decodeBase64(part.body.data)
        }
        return ""
    }

    private fun decodeBase64(data: String): String {
        return try {
            val decodedBytes = Base64.decode(data, Base64.URL_SAFE)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
