package com.example.myalitrecker.data.remote.aliexpress

data class AliExpressOrder(
    val orderId: String,
    val orderStatus: String = "Ожидает отправки",
    val trackingNumber: String? = null,
    val carrier: String? = null,
    val orderDate: Long = System.currentTimeMillis(),
    val isConsolidated: Boolean = false,
    val items: List<AliExpressOrderItem> = emptyList()
)

data class AliExpressOrderItem(
    val orderId: String,
    val title: String,
    val imageUrl: String? = null,
    val price: String? = null,
    val quantity: Int = 1,
    val productUrl: String? = null
)
