package com.example.myalitrecker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = ParcelEntity::class,
            parentColumns = ["trackingNumber"],
            childColumns = ["trackingNumber"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackingNumber"])]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderId: String,
    val trackingNumber: String,
    val title: String,
    val imageUrl: String? = null,
    val price: String? = null,
    val quantity: Int = 1
)
