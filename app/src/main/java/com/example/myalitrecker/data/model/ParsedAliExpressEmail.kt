package com.example.myalitrecker.data.model

data class ParsedAliExpressEmail(
    val emailId: String,
    val orderId: String?,
    val orderIds: List<String> = emptyList(),
    val trackingNumber: String?,
    val items: List<ParsedItem>,
    val date: Long,
    val isConsolidated: Boolean = false,
    val carrier: String? = null
)

data class ParsedItem(
    val orderId: String,
    val title: String,
    val imageUrl: String?,
    val price: String? = null,
    val quantity: Int = 1
)
