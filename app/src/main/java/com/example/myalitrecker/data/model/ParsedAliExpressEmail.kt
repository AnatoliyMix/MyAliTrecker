package com.example.myalitrecker.data.model

data class ParsedAliExpressEmail(
    val emailId: String,
    val orderId: String?,
    val trackingNumber: String?,
    val items: List<ParsedItem>,
    val date: Long
)

data class ParsedItem(
    val orderId: String,
    val title: String,
    val imageUrl: String?,
    val price: String? = null
)
